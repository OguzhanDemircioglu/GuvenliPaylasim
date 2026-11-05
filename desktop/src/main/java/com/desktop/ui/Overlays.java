package com.desktop.ui;

import atlantafx.base.controls.ModalPane;
import javafx.scene.layout.VBox;

/** Pencerenin üst katmanları (diyalog ve bildirim) uygulama açılırken bir kez bağlanır. */
public final class Overlays {

    private Overlays() {
    }

    public static void install(ModalPane modal, VBox toastLayer) {
        Dialogs.install(modal);
        Toasts.install(toastLayer);
    }
}
