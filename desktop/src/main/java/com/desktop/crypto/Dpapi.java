package com.desktop.crypto;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Windows veri koruması (DPAPI, {@code CryptProtectData}): veriyi o Windows kullanıcısının oturum anahtarıyla
 * şifreler. Şifreli veri başka bir Windows kullanıcısında ya da başka bir bilgisayarda açılmaz. "Beni hatırla"
 * kaydı bununla korunur (Crypto_Design §7.4, Security.md). Java'nın yerleşik FFM arayüzüyle çağrılır; ek kütüphane
 * ya da imzasız DLL gerekmez.
 */
public final class Dpapi {

    private static final int CRYPTPROTECT_UI_FORBIDDEN = 0x1;
    /** {@code DATA_BLOB { DWORD cbData; BYTE *pbData; }} — 64 bit Windows'ta işaretçiden önce 4 bayt boşluk. */
    private static final StructLayout BLOB = MemoryLayout.structLayout(
            JAVA_INT.withName("cbData"), MemoryLayout.paddingLayout(4), ADDRESS.withName("pbData"));
    private static final long DATA_OFFSET = 8;

    private Dpapi() {
    }

    /** {@code entropy}: uygulamaya özgü ek değer; açarken aynısı verilmezse veri açılmaz. */
    public static byte[] protect(byte[] data, byte[] entropy) {
        return call(Native.PROTECT, data, entropy);
    }

    public static byte[] unprotect(byte[] protectedData, byte[] entropy) {
        return call(Native.UNPROTECT, protectedData, entropy);
    }

    private static byte[] call(MethodHandle function, byte[] input, byte[] entropy) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment in = blob(arena, input);
            MemorySegment extra = blob(arena, entropy);
            MemorySegment out = arena.allocate(BLOB);
            int ok = (int) function.invokeExact(in, MemorySegment.NULL, extra, MemorySegment.NULL,
                    MemorySegment.NULL, CRYPTPROTECT_UI_FORBIDDEN, out);
            if (ok == 0) {
                throw new CryptoException("Windows veri koruması başarısız oldu", null);
            }
            int length = out.get(JAVA_INT, 0);
            MemorySegment data = out.get(ADDRESS, DATA_OFFSET).reinterpret(length);
            try {
                return data.toArray(JAVA_BYTE);
            } finally {
                MemorySegment ignored = (MemorySegment) Native.LOCAL_FREE.invokeExact(data);
            }
        } catch (CryptoException e) {
            throw e;
        } catch (Throwable e) {
            throw new CryptoException("Windows veri koruması çağrılamadı", e);
        }
    }

    private static MemorySegment blob(Arena arena, byte[] bytes) {
        MemorySegment blob = arena.allocate(BLOB);
        MemorySegment data = arena.allocate(Math.max(1, bytes.length));
        MemorySegment.copy(bytes, 0, data, JAVA_BYTE, 0, bytes.length);
        blob.set(JAVA_INT, 0, bytes.length);
        blob.set(ADDRESS, DATA_OFFSET, data);
        return blob;
    }

    /** Sadece Windows'ta ve ilk kullanımda yüklenir. */
    private static final class Native {

        static final MethodHandle PROTECT;
        static final MethodHandle UNPROTECT;
        static final MethodHandle LOCAL_FREE;

        static {
            Linker linker = Linker.nativeLinker();
            SymbolLookup crypt32 = SymbolLookup.libraryLookup("crypt32", Arena.global());
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            FunctionDescriptor signature = FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS);
            PROTECT = linker.downcallHandle(crypt32.find("CryptProtectData").orElseThrow(), signature);
            UNPROTECT = linker.downcallHandle(crypt32.find("CryptUnprotectData").orElseThrow(), signature);
            LOCAL_FREE = linker.downcallHandle(kernel32.find("LocalFree").orElseThrow(),
                    FunctionDescriptor.of(ADDRESS, ADDRESS));
        }
    }
}
