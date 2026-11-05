package com.desktop.ui.views;

import atlantafx.base.theme.Styles;
import com.desktop.api.Dto;
import com.desktop.ui.Dialogs;
import com.desktop.ui.Formats;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.List;

/**
 * Gönderim sırasında yapılan kriptografik adımları sırayla gösterir. Şifreleme gerçekte milisaniyeler sürer;
 * adımlar kullanıcının ne olduğunu görebilmesi için kısa aralıklarla işaretlenir. Son adım sunucunun cevabını bekler.
 */
final class SendProgress {

    private record Step(HBox row, FontIcon icon) {
    }

    private final Runnable onSent;
    private final VBox steps = new VBox(2);
    private final List<Step> items;
    private final VBox dialog = new VBox(18);
    private int revealed;
    private Dto.DocumentSent result;
    private Throwable failure;

    SendProgress(List<Dto.Recipient> recipients, long plainSize, Runnable onSent) {
        this.onSent = onSent;
        long people = recipients.size();
        items = List.of(
                step("Belgeye özel anahtar üretildi", "256 bitlik rastgele AES anahtarı, sadece bu belge için"),
                step("İçerik şifrelendi", "AES-256-GCM · " + Formats.size(plainSize) + " · değiştirilirse açılmaz"),
                step("Belge imzalandı", "Ed25519 · alıcılar belgenin sizden geldiğini doğrulayacak"),
                step("Anahtar alıcılara sarmalandı", "X25519 + HKDF · " + people + " kişinin her biri için ayrı ayrı"),
                step("Sunucuya şifreli olarak gönderildi", "Sunucu içeriği ve belge anahtarını göremez"));
        items.forEach(s -> steps.getChildren().add(s.row()));
    }

    void show() {
        dialog.getStyleClass().add("dialog");
        dialog.setPadding(new Insets(28));
        dialog.setPrefWidth(500);
        dialog.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        dialog.getChildren().setAll(
                Ui.row(14, Ui.iconBadge(Material2OutlinedAL.ENHANCED_ENCRYPTION, "accent", 48),
                        Ui.column(2, Ui.label("Belge şifreleniyor", Styles.TITLE_3),
                                Ui.muted("Bütün işlemler bu bilgisayarda yapılıyor"))),
                steps);
        Dialogs.showPersistent(dialog);
        activate(0);

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(420), e -> advance()));
        timeline.setCycleCount(items.size() - 1);
        timeline.play();
    }

    void completed(Dto.DocumentSent sent) {
        result = sent;
        finishIfReady();
    }

    void failed(Throwable error) {
        failure = error;
        finishIfReady();
    }

    private void advance() {
        done(revealed);
        revealed++;
        activate(revealed);
        finishIfReady();
    }

    private void finishIfReady() {
        boolean animationDone = revealed >= items.size() - 1;
        if (failure != null) {
            showFailure();
        } else if (result != null && animationDone) {
            done(items.size() - 1);
            showSuccess();
        }
    }

    private void showSuccess() {
        Button close = Ui.primary("Tamam", Material2OutlinedAL.DONE);
        close.setDefaultButton(true);
        close.setOnAction(e -> Dialogs.close());

        VBox sms = new VBox();
        if (result.smsNotified() > 0) {
            Label smsText = new Label(result.smsNotified() + " alıcıya \"yeni belgeniz var\" SMS bildirimi gönderildi "
                    + "(simülasyon). SMS'te parola ya da anahtar yoktur.", Ui.icon(Material2OutlinedMZ.SMS, 18));
            smsText.setWrapText(true);
            smsText.setGraphicTextGap(10);
            sms.getChildren().add(smsText);
            sms.getStyleClass().add("recipient-summary");
        }
        HBox footer = Ui.row(10, Ui.grow(), close);
        dialog.getChildren().setAll(
                Ui.row(14, Ui.iconBadge(Material2OutlinedAL.CHECK_CIRCLE, "success", 48),
                        Ui.column(2, Ui.label("Gönderildi", Styles.TITLE_3),
                                Ui.muted("Belge No " + result.id() + " · " + result.recipientCount() + " alıcı"))),
                steps, sms, footer);
        Toasts.success("Belge " + result.recipientCount() + " alıcıya şifreli olarak gönderildi.");
        onSent.run();
    }

    private void showFailure() {
        Button close = Ui.button("Kapat", null);
        close.setOnAction(e -> Dialogs.close());
        Label reason = Ui.muted(Toasts.describe(failure));
        dialog.getChildren().setAll(
                Ui.row(14, Ui.iconBadge(Material2OutlinedAL.ERROR_OUTLINE, "danger", 48),
                        Ui.column(2, Ui.label("Gönderilemedi", Styles.TITLE_3), reason)),
                Ui.row(10, Ui.grow(), close));
    }

    private void activate(int index) {
        if (index < items.size()) {
            Step step = items.get(index);
            step.row().getStyleClass().add("active");
            step.icon().setIconCode(Material2OutlinedAL.HOURGLASS_EMPTY);
        }
    }

    private void done(int index) {
        Step step = items.get(index);
        step.row().getStyleClass().remove("active");
        step.row().getStyleClass().add("done");
        step.icon().setIconCode(Material2OutlinedAL.CHECK_CIRCLE);
    }

    private static Step step(String title, String detail) {
        FontIcon icon = Ui.icon(Material2OutlinedMZ.RADIO_BUTTON_UNCHECKED, 20);
        icon.getStyleClass().add("step-icon");
        VBox text = Ui.column(1, Ui.label(title, "step-title"), Ui.label(detail, "step-detail"));
        HBox row = Ui.row(12, icon, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("step-row");
        return new Step(row, icon);
    }
}
