package com.desktop.ui;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;

/**
 * Pencere içinde açılan modal diyaloglar (işletim sisteminin gri diyalogları yerine). Arka plan karartılır.
 */
public final class Dialogs {

    private static ModalPane modal;

    private Dialogs() {
    }

    static void install(ModalPane modalPane) {
        modal = modalPane;
    }

    /** Başlık, içerik ve alttaki butonlardan oluşan bir diyalog açar. */
    public static VBox show(String title, Node body, Button... actions) {
        Label heading = Ui.label(title, Styles.TITLE_3);
        HBox footer = new HBox(10, actions);
        footer.setAlignment(Pos.CENTER_RIGHT);
        VBox dialog = new VBox(18, heading, body, footer);
        dialog.getStyleClass().add("dialog");
        dialog.setPadding(new Insets(24));
        dialog.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        dialog.setMinWidth(440);
        modal.show(dialog);
        return dialog;
    }

    /** Kapatılamayan diyalog (ör. zorunlu güncelleme, işlem sürerken). */
    public static void showPersistent(Node content) {
        modal.setPersistent(true);
        modal.show(content);
    }

    public static void close() {
        modal.setPersistent(false);
        modal.hide(true);
    }

    public static void confirm(String title, String message, String confirmText, Ikon ikon, boolean danger,
                               Runnable onConfirm) {
        Button cancel = Ui.button("Vazgeç", null);
        Button ok = Ui.button(confirmText, ikon, danger ? Styles.DANGER : Styles.ACCENT);
        cancel.setOnAction(e -> close());
        ok.setOnAction(e -> {
            close();
            onConfirm.run();
        });
        cancel.setCancelButton(true);
        Label text = Ui.muted(message);
        text.setMaxWidth(420);
        show(title, text, cancel, ok);
    }
}
