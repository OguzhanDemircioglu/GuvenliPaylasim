package com.desktop.ui.views;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import com.desktop.api.Dto;
import com.desktop.ui.Async;
import com.desktop.ui.Dialogs;
import com.desktop.ui.Inputs;
import com.desktop.ui.PasswordFields;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.List;
import java.util.Map;

/**
 * İsteğe bağlı iki adımlı doğrulama (Crypto_Design §7.4). Açıkken girişte ve parola sıfırlamada Authenticator kodu ya da
 * tek kullanımlık kurtarma kodu istenir; numarası kopyalanan biri parolayı sıfırlayıp hesabı ele geçiremez.
 */
final class TwoFactorCard extends VBox {

    private final Context context;

    TwoFactorCard(Context context) {
        this.context = context;
        getStyleClass().add("surface");
        setSpacing(12);
        render();
    }

    private void render() {
        boolean on = context.session.totpEnabled();
        Label status = Ui.chip(on ? "Açık" : "Kapalı", on ? "success" : "neutral");
        Label text = Ui.muted(on
                ? "Girişte ve şifre sıfırlamada Authenticator kodu isteniyor. Telefonunuzu kaybederseniz kurtarma "
                        + "kodlarınızdan birini kullanın."
                : "İsteğe bağlı. Açarsanız girişte ve şifre sıfırlamada Authenticator uygulamasındaki kod da istenir; "
                        + "numaranız kopyalansa bile hesabınız ele geçirilemez.");
        text.setWrapText(true);
        Button action = on
                ? Ui.button("Kapat", Material2OutlinedAL.LOCK_OPEN, Styles.BUTTON_OUTLINED)
                : Ui.primary("Aç", Material2OutlinedAL.LOCK);
        action.setOnAction(e -> {
            if (on) {
                openDisable();
            } else {
                startSetup(action);
            }
        });
        getChildren().setAll(Ui.row(10, Ui.label("İki Adımlı Doğrulama", "section-title"), Ui.grow(), status), text,
                action);
    }

    // ---------- Açma ----------

    private void startSetup(Button action) {
        action.setDisable(true);
        Async.run(() -> context.auth.setupTotp(context.session), setup -> {
            action.setDisable(false);
            showSetup(setup);
        }, failure -> {
            action.setDisable(false);
            Toasts.error(failure);
        });
    }

    private void showSetup(Dto.TotpSetup setup) {
        ImageView qr = new ImageView(qr(setup.uri(), 200));
        Label secret = Ui.label(grouped(setup.secret()), "fingerprint");
        Button copy = Ui.button("Anahtarı Kopyala", Material2OutlinedAL.CONTENT_COPY, Styles.FLAT);
        copy.setOnAction(e -> copy(setup.secret(), "Anahtar panoya kopyalandı."));
        Label help = Ui.muted("1. Authenticator uygulamanızda (Google Authenticator, Microsoft Authenticator vb.) QR kodu "
                + "okutun ya da anahtarı elle girin.\n2. Uygulamanın gösterdiği 6 haneli kodu aşağıya yazın.");
        help.setWrapText(true);
        help.setMaxWidth(440);
        TextField code = Inputs.digits(new TextField(), 6);
        code.setPromptText("123456");
        code.getStyleClass().add("code-input");
        Label status = errorLabel();

        Button cancel = Ui.button("Vazgeç", null);
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> Dialogs.close());
        Button confirm = Ui.primary("Doğrula ve Aç", Material2OutlinedAL.LOCK);
        confirm.setDefaultButton(true);
        confirm.setOnAction(e -> {
            confirm.setDisable(true);
            Async.run(() -> context.auth.enableTotp(context.session, code.getText()), codes -> {
                Dialogs.close();
                render();
                showRecoveryCodes(codes);
            }, failure -> {
                confirm.setDisable(false);
                showError(status, failure);
            });
        });
        VBox body = new VBox(12, help, Ui.row(16, qr, new VBox(8, Ui.muted("Anahtar"), secret, copy)), code, status);
        Dialogs.show("İki Adımlı Doğrulamayı Aç", body, cancel, confirm);
        code.requestFocus();
    }

    /** Kurtarma kodları sadece burada, bir kez gösterilir. */
    private static void showRecoveryCodes(List<String> codes) {
        FlowPane grid = new FlowPane(10, 8);
        grid.setPrefWrapLength(420);
        codes.forEach(c -> grid.getChildren().add(Ui.label(c, "fingerprint")));
        Label warning = Ui.muted("Bu kodlar bir daha gösterilmez. Kâğıda yazın ya da güvenli bir yerde saklayın. "
                + "Authenticator'a erişemezseniz her biri bir kez giriş ya da şifre sıfırlama için kullanılabilir.");
        warning.setWrapText(true);
        warning.setMaxWidth(440);
        Button copy = Ui.button("Hepsini Kopyala", Material2OutlinedAL.CONTENT_COPY, Styles.BUTTON_OUTLINED);
        copy.setOnAction(e -> copy(String.join("\n", codes), "Kurtarma kodları panoya kopyalandı."));
        Button done = Ui.primary("Kaydettim", Material2OutlinedAL.DONE);
        done.setDefaultButton(true);
        done.setOnAction(e -> {
            Dialogs.close();
            Toasts.success("İki adımlı doğrulama açıldı.");
        });
        Dialogs.show("Kurtarma Kodlarınız", new VBox(14, warning, grid), copy, done);
    }

    // ---------- Kapatma ----------

    private void openDisable() {
        PasswordTextField password = PasswordFields.limit(new PasswordTextField());
        password.setPromptText("Mevcut Şifre");
        TextField code = new TextField();
        code.setPromptText("Authenticator kodu ya da kurtarma kodu");
        Label status = errorLabel();
        Button cancel = Ui.button("Vazgeç", null);
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> Dialogs.close());
        Button confirm = Ui.button("Kapat", Material2OutlinedAL.LOCK_OPEN, Styles.DANGER);
        confirm.setDefaultButton(true);
        confirm.setOnAction(e -> {
            confirm.setDisable(true);
            Async.run(() -> {
                context.auth.disableTotp(context.session, password.getPassword().toCharArray(), code.getText());
                return null;
            }, ignored -> {
                Dialogs.close();
                render();
                Toasts.success("İki adımlı doğrulama kapatıldı; kurtarma kodları geçersiz oldu.");
            }, failure -> {
                confirm.setDisable(false);
                showError(status, failure);
            });
        });
        Label help = Ui.muted("Kapatınca girişte sadece şifre istenir ve kurtarma kodlarınız geçersiz olur.");
        help.setWrapText(true);
        help.setMaxWidth(420);
        Dialogs.show("İki Adımlı Doğrulamayı Kapat", new VBox(12, help, password, code, status), cancel, confirm);
    }

    // ---------- Yardımcılar ----------

    static WritableImage qr(String content, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8"));
            WritableImage image = new WritableImage(matrix.getWidth(), matrix.getHeight());
            PixelWriter pixels = image.getPixelWriter();
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    pixels.setArgb(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            return image;
        } catch (WriterException e) {
            throw new IllegalStateException("QR code could not be drawn", e);
        }
    }

    private static String grouped(String secret) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < secret.length(); i += 4) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(secret, i, Math.min(secret.length(), i + 4));
        }
        return out.toString();
    }

    private static void copy(String text, String message) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        Toasts.info(message);
    }

    private static Label errorLabel() {
        Label label = new Label();
        label.setWrapText(true);
        label.setMaxWidth(420);
        label.getStyleClass().add("form-error");
        label.setVisible(false);
        label.setManaged(false);
        return label;
    }

    private static void showError(Label label, Throwable failure) {
        label.setText(Toasts.describe(failure));
        label.setVisible(true);
        label.setManaged(true);
    }
}
