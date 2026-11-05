package com.desktop.ui;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Ağ ve kripto işleri JavaFX thread'ini dondurmasın diye arka planda çalışır; sonuç JavaFX thread'ine döner.
 */
public final class Async {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "gps-worker");
        thread.setDaemon(true);
        return thread;
    });

    private Async() {
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        EXECUTOR.submit(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable e) {
                Platform.runLater(() -> onError.accept(e));
            }
        });
    }

    /** Hata kullanıcıya bildirim olarak gösterilir. */
    public static <T> void run(Callable<T> work, Consumer<T> onSuccess) {
        run(work, onSuccess, Toasts::error);
    }
}
