package com.desktop.ui;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Uygulama pencerelerini ekran görüntüsünden ve ekran kaydından hariç tutar ({@code SetWindowDisplayAffinity},
 * {@code WDA_EXCLUDEFROMCAPTURE}, Windows 10 2004+). Win+Shift+S, PrintScreen, PowerToys Metin Ayıklayıcı, ekran
 * paylaşımı ve kayıt programları pencereyi boş görür. "Kaydedilemez" belge açıkken kullanılır (Security.md S4).
 * Telefonla ekranın fotoğrafını, sanal makine/uzak masaüstü dışından alınan görüntüyü ve değiştirilmiş uygulamayı
 * engellemez: caydırıcıdır.
 * <p>
 * JavaFX pencereleri JavaFX uygulama iş parçacığında açılır; o iş parçacığının bütün üst düzey pencerelerine uygulanır.
 * Sadece JavaFX iş parçacığından çağrılır.
 */
public final class ScreenGuard {

    private static final int WDA_NONE = 0x0;
    private static final int WDA_MONITOR = 0x1;
    private static final int WDA_EXCLUDEFROMCAPTURE = 0x11;
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final List<MemorySegment> FOUND = new ArrayList<>();

    private ScreenGuard() {
    }

    /** Korumayı açar ya da kapatır; açarken en az bir pencere korunamadıysa false döner. */
    public static boolean set(boolean on) {
        if (!WINDOWS) {
            return !on;
        }
        try {
            FOUND.clear();
            int thread = (int) Native.CURRENT_THREAD.invokeExact();
            int ignored = (int) Native.ENUM_THREAD_WINDOWS.invokeExact(thread, Native.CALLBACK, 0L);
            boolean all = !FOUND.isEmpty();
            for (MemorySegment window : FOUND) {
                int ok = (int) Native.SET_AFFINITY.invokeExact(window, on ? WDA_EXCLUDEFROMCAPTURE : WDA_NONE);
                if (ok == 0 && on) {
                    // Eski Windows: pencere görüntüde siyah çıkar.
                    ok = (int) Native.SET_AFFINITY.invokeExact(window, WDA_MONITOR);
                }
                all &= ok != 0;
            }
            return all;
        } catch (Throwable e) {
            return false;
        } finally {
            FOUND.clear();
        }
    }

    /** {@code BOOL CALLBACK EnumThreadWndProc(HWND, LPARAM)}: görünür pencereler toplanır. */
    private static int collect(MemorySegment window, long ignored) {
        try {
            if ((int) Native.IS_VISIBLE.invokeExact(window) != 0) {
                FOUND.add(window);
            }
        } catch (Throwable e) {
            // Bu pencere atlanır.
        }
        return 1;
    }

    /** Sadece Windows'ta ve ilk kullanımda yüklenir. */
    private static final class Native {

        static final MethodHandle CURRENT_THREAD;
        static final MethodHandle ENUM_THREAD_WINDOWS;
        static final MethodHandle IS_VISIBLE;
        static final MethodHandle SET_AFFINITY;
        static final MemorySegment CALLBACK;

        static {
            Linker linker = Linker.nativeLinker();
            SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            CURRENT_THREAD = linker.downcallHandle(kernel32.findOrThrow("GetCurrentThreadId"),
                    FunctionDescriptor.of(JAVA_INT));
            ENUM_THREAD_WINDOWS = linker.downcallHandle(user32.findOrThrow("EnumThreadWindows"),
                    FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG));
            IS_VISIBLE = linker.downcallHandle(user32.findOrThrow("IsWindowVisible"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS));
            SET_AFFINITY = linker.downcallHandle(user32.findOrThrow("SetWindowDisplayAffinity"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
            try {
                MethodHandle target = MethodHandles.lookup().findStatic(ScreenGuard.class, "collect",
                        MethodType.methodType(int.class, MemorySegment.class, long.class));
                CALLBACK = linker.upcallStub(target, FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG), Arena.global());
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
}
