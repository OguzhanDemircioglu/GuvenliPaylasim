package com.desktop.ui.views;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.desktop.crypto.UserKeys;
import com.desktop.service.PasswordStrength;
import com.desktop.ui.Async;
import com.desktop.ui.Dialogs;
import com.desktop.ui.Formats;
import com.desktop.ui.Inputs;
import com.desktop.ui.PasswordFields;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

/** Kullanıcının anahtarları: parmak izi, kullanılan algoritmalar, parola değiştirme ve iki adımlı doğrulama. */
final class SecurityView extends ScrollPane {

    SecurityView(Context context) {
        UserKeys keys = context.session.keys();

        Label fingerprint = Ui.label(keys.fingerprint(), "fingerprint");
        VBox fingerprintCard = Ui.surface(
                Ui.row(12, Ui.iconBadge(Material2OutlinedAL.FINGERPRINT, "accent", 44),
                        Ui.column(2, Ui.label("Anahtar Parmak İziniz", "section-title"),
                                Ui.muted("Açık anahtarlarınızın özeti. Gizli değildir."))),
                fingerprint,
                Ui.muted("Bir meslektaşınızla telefonda ya da yüz yüze bu değeri karşılaştırarak, sunucunun araya "
                        + "sahte bir anahtar koymadığından emin olabilirsiniz."));

        GridPane algorithms = new GridPane(18, 14);
        addAlgorithm(algorithms, 0, Material2OutlinedAL.LOCK, "Şifreleme Anahtarı", "X25519 (Curve25519)",
                "Size gönderilen belgelerin anahtarı bununla sarmalanır.");
        addAlgorithm(algorithms, 1, Material2OutlinedMZ.VERIFIED_USER, "İmza Anahtarı", "Ed25519",
                "Gönderdiğiniz her belge bununla imzalanır.");
        addAlgorithm(algorithms, 2, Material2OutlinedAL.ENHANCED_ENCRYPTION, "Belge Şifreleme", "AES-256-GCM",
                "Her belgeye özel rastgele anahtar; değişiklik anında fark edilir.");
        addAlgorithm(algorithms, 3, Material2OutlinedMZ.VPN_KEY, "Şifre Koruması", "PBKDF2-SHA256 · 600.000 tur",
                "Özel anahtarlarınız şifrenizden türetilen anahtarla şifrelidir; şifreniz sunucuya gitmez.");
        VBox algorithmCard = Ui.surface(Ui.label("Kullanılan Algoritmalar", "section-title"), algorithms);

        PasswordForm form = new PasswordForm(context.auth, context.session, "Şifreyi Değiştir",
                () -> Toasts.success("Şifreniz değişti; özel anahtarlarınız yeni şifreyle yeniden şifrelendi."));
        form.setMaxWidth(380);
        VBox passwordCard = Ui.surface(Ui.label("Şifreyi Değiştir", "section-title"),
                Ui.muted("Anahtarlarınız değişmez; sadece onları koruyan şifre değişir. Size gelen belgeler "
                        + "açılmaya devam eder."), form);

        VBox left = new VBox(20, profileCard(context), fingerprintCard, algorithmCard);
        HBox.setHgrow(left, Priority.ALWAYS);
        VBox right = new VBox(20, passwordCard, new TwoFactorCard(context), deleteCard(context));
        right.setPrefWidth(460);
        right.setMinWidth(420);
        HBox body = new HBox(20, left, right);

        VBox page = new VBox(Ui.header("Profilim", "Adınız, telefonunuz ve belgelerinizi açan anahtarlar"), body);
        page.getStyleClass().add("page");
        setContent(page);
        setFitToWidth(true);
        getStyleClass().add(Tweaks.EDGE_TO_EDGE);
        setStyle("-fx-background-color: transparent; -fx-background: transparent;");
    }

    /** Hesabı ve ilgili her şeyi kalıcı olarak siler (Crypto_Design §7.13). Geri alınamaz; parola istenir. */
    private static VBox deleteCard(Context context) {
        Button open = Ui.button("Hesabımı Sil", Material2OutlinedAL.DELETE_FOREVER, Styles.DANGER);
        open.setOnAction(e -> confirmDelete(context));
        return Ui.surface(Ui.label("Hesabı Sil", "section-title"),
                Ui.muted("Hesabınız ve onunla ilgili her şey sunucudan kalıcı olarak silinir. Geri alınamaz."), open);
    }

    private static void confirmDelete(Context context) {
        Label warning = Ui.muted("Şunlar kalıcı olarak silinir ve geri alınamaz: hesabınız, anahtarlarınız, size gelen "
                + "ve sizin gönderdiğiniz bütün kutular (alıcılarından da), kişi listeniz (karşı tarafların listesinden "
                + "de siz çıkarsınız) ve davetleriniz. Aynı numarayla daha sonra yeniden kayıt olabilirsiniz, ama eski "
                + "hiçbir şey geri gelmez.");
        warning.setWrapText(true);
        warning.setMaxWidth(440);
        PasswordTextField password = PasswordFields.limit(new PasswordTextField());
        password.setPromptText("Şifreniz");
        TextField totp = new TextField();
        totp.setPromptText("Authenticator ya da kurtarma kodu");
        boolean needsTotp = context.session.totpEnabled();
        totp.setVisible(needsTotp);
        totp.setManaged(needsTotp);
        Label error = Ui.label("", "form-error");
        error.setWrapText(true);
        error.setMaxWidth(440);
        error.setManaged(false);

        Button cancel = Ui.button("Vazgeç", null);
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> Dialogs.close());
        Button delete = Ui.button("Hesabımı Kalıcı Olarak Sil", Material2OutlinedAL.DELETE_FOREVER, Styles.DANGER);
        javafx.beans.property.BooleanProperty busy = new javafx.beans.property.SimpleBooleanProperty(false);
        delete.disableProperty().bind(busy.or(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> PasswordStrength.composition(password.getPassword()) != null
                        || (needsTotp && totp.getText().isBlank()),
                password.passwordProperty(), totp.textProperty())));
        delete.setOnAction(e -> {
            busy.set(true);
            error.setManaged(false);
            error.setText("");
            char[] secret = password.getPassword().toCharArray();
            String code = totp.getText();
            Async.run(() -> {
                        context.auth.deleteAccount(context.session, secret, code);
                        return true;
                    },
                    ok -> {
                        Dialogs.close();
                        Toasts.success("Hesabınız ve bütün verileriniz silindi.");
                        context.onAccountDeleted.run();
                    },
                    failure -> {
                        busy.set(false);
                        error.setText(Toasts.describe(failure));
                        error.setManaged(true);
                    });
        });
        Dialogs.show("Hesabınızı Silmek Üzeresiniz", new VBox(12, warning, password, totp, error), cancel, delete);
        password.requestFocus();
    }

    /** Ad soyad düzenlenir (sadece harf); telefon salt okunurdur, çünkü parola telefonla türetilir. */
    private static VBox profileCard(Context context) {
        TextField name = Inputs.name(new TextField(context.session.fullName()));
        name.setMaxWidth(380);
        TextField phone = new TextField(Formats.phone(context.session.user().phone()));
        phone.setEditable(false);
        phone.setFocusTraversable(false);
        phone.setMaxWidth(380);
        Label error = Ui.label("", "form-error");
        error.setWrapText(true);
        error.setManaged(false);
        Button save = Ui.primary("Adı Kaydet", Material2OutlinedMZ.SAVE);
        save.disableProperty().bind(name.textProperty()
                .map(text -> Inputs.normalizeName(text).equals(context.session.fullName())
                        || Inputs.nameError(text) != null));
        // Düğme kapalıysa nedeni yazılır (ör. tek kelime).
        name.textProperty().addListener((obs, old, text) -> {
            String invalid = text.isBlank() ? null : Inputs.nameError(text);
            error.setText(invalid == null ? "" : invalid);
            error.setManaged(invalid != null);
        });
        save.setOnAction(e -> {
            String invalid = Inputs.nameError(name.getText());
            error.setText(invalid == null ? "" : invalid);
            error.setManaged(invalid != null);
            if (invalid != null) {
                return;
            }
            save.disableProperty().unbind();
            save.setDisable(true);
            Async.run(() -> {
                        context.auth.updateName(context.session, Inputs.normalizeName(name.getText()));
                        return true;
                    },
                    ok -> {
                        name.setText(context.session.fullName());
                        save.disableProperty().bind(name.textProperty()
                                .map(text -> Inputs.normalizeName(text).equals(context.session.fullName())
                                        || Inputs.nameError(text) != null));
                        context.onProfileChanged.run();
                        Toasts.success("Adınız güncellendi.");
                    },
                    failure -> {
                        save.setDisable(false);
                        error.setText(Toasts.describe(failure));
                        error.setManaged(true);
                    });
        });
        return Ui.surface(Ui.label("Profil", "section-title"),
                Ui.label("Ad Soyad", "field-label"), name,
                Ui.label("Cep Telefonu", "field-label"), phone,
                Ui.muted("Telefon numarası değiştirilemez: şifreniz ve anahtarlarınız bu numaraya bağlıdır."),
                error, save);
    }

    private static void addAlgorithm(GridPane grid, int row, Ikon ikon, String title, String value, String detail) {
        Node icon = Ui.iconBadge(ikon, "neutral", 36);
        Label description = Ui.muted(detail);
        grid.add(icon, 0, row);
        grid.add(Ui.column(2, Ui.label(title, Styles.TEXT_BOLD), description), 1, row);
        grid.add(Ui.chip(value, "accent"), 2, row);
        GridPane.setHgrow(description, Priority.ALWAYS);
    }
}
