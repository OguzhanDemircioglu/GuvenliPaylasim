package com.desktop.ui.views;

import atlantafx.base.theme.Styles;
import com.desktop.service.InviteService;
import com.desktop.ui.Dialogs;
import com.desktop.ui.Formats;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;

/** Davet kodu sadece burada, bir kez gösterilir; hiçbir yere kaydedilmez (Yeni Gönderi ve Kişi Ekle). */
final class InviteCodeDialog {

    private InviteCodeDialog() {
    }

    static void show(InviteService.SentCode sent) {
        Label code = Ui.label(sent.code(), "invite-code");
        Button copy = Ui.button("Kopyala", Material2OutlinedAL.CONTENT_COPY, Styles.BUTTON_OUTLINED);
        copy.setOnAction(e -> {
            ClipboardContent clip = new ClipboardContent();
            clip.putString(sent.code());
            Clipboard.getSystemClipboard().setContent(clip);
            Toasts.info("Kod panoya kopyalandı.");
        });
        Label text = Ui.muted("Bu kodu " + Formats.phone(sent.recipientPhone()) + " numaralı kişiye kendi kanalınızla "
                + "(yüz yüze, telefonla, başka bir uygulamayla) iletin. Kod sunucuya gönderilmedi ve bir daha "
                + "gösterilemez. Son geçerlilik: " + Formats.fullDate(sent.expiresAt()) + ".");
        text.setWrapText(true);
        text.setMaxWidth(440);
        Button done = Ui.primary("Tamam", Material2OutlinedAL.DONE);
        done.setDefaultButton(true);
        done.setOnAction(e -> Dialogs.close());
        Dialogs.show("Davet Kodu", new VBox(14, Ui.row(12, code, copy), text), done);
    }
}
