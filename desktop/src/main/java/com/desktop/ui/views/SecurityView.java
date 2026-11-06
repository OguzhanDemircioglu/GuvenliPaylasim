package com.desktop.ui.views;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.desktop.crypto.UserKeys;
import com.desktop.ui.Async;
import com.desktop.ui.Formats;
import com.desktop.ui.Inputs;
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
                        Ui.column(2, Ui.label("Anahtar parmak iziniz", "section-title"),
                                Ui.muted("Açık anahtarlarınızın özeti. Gizli değildir."))),
                fingerprint,
                Ui.muted("Bir meslektaşınızla telefonda ya da yüz yüze bu değeri karşılaştırarak, sunucunun araya "
                        + "sahte bir anahtar koymadığından emin olabilirsiniz."));

        GridPane algorithms = new GridPane(18, 14);
        addAlgorithm(algorithms, 0, Material2OutlinedAL.LOCK, "Şifreleme anahtarı", "X25519 (Curve25519)",
                "Size gönderilen belgelerin anahtarı bununla sarmalanır.");
        addAlgorithm(algorithms, 1, Material2OutlinedMZ.VERIFIED_USER, "İmza anahtarı", "Ed25519",
                "Gönderdiğiniz her belge bununla imzalanır.");
        addAlgorithm(algorithms, 2, Material2OutlinedAL.ENHANCED_ENCRYPTION, "Belge şifreleme", "AES-256-GCM",
                "Her belgeye özel rastgele anahtar; değişiklik anında fark edilir.");
        addAlgorithm(algorithms, 3, Material2OutlinedMZ.VPN_KEY, "Parola koruması", "PBKDF2-SHA256 · 600.000 tur",
                "Özel anahtarlarınız parolanızdan türetilen anahtarla şifrelidir; parolanız sunucuya gitmez.");
        VBox algorithmCard = Ui.surface(Ui.label("Kullanılan algoritmalar", "section-title"), algorithms);

        PasswordForm form = new PasswordForm(context.auth, context.session, "Parolayı değiştir",
                () -> Toasts.success("Parolanız değişti; özel anahtarlarınız yeni parolayla yeniden şifrelendi."));
        form.setMaxWidth(380);
        VBox passwordCard = Ui.surface(Ui.label("Parolayı değiştir", "section-title"),
                Ui.muted("Anahtarlarınız değişmez; sadece onları koruyan parola değişir. Size gelen belgeler "
                        + "açılmaya devam eder."), form);

        VBox left = new VBox(20, profileCard(context), fingerprintCard, algorithmCard);
        HBox.setHgrow(left, Priority.ALWAYS);
        VBox right = new VBox(20, passwordCard, new TwoFactorCard(context));
        right.setPrefWidth(420);
        right.setMinWidth(380);
        HBox body = new HBox(20, left, right);

        VBox page = new VBox(Ui.header("Profilim", "Adınız, telefonunuz ve belgelerinizi açan anahtarlar"), body);
        page.getStyleClass().add("page");
        setContent(page);
        setFitToWidth(true);
        getStyleClass().add(Tweaks.EDGE_TO_EDGE);
        setStyle("-fx-background-color: transparent; -fx-background: transparent;");
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
        Button save = Ui.primary("Adı kaydet", Material2OutlinedMZ.SAVE);
        save.disableProperty().bind(name.textProperty()
                .map(text -> Inputs.normalizeName(text).equals(context.session.fullName())));
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
                                .map(text -> Inputs.normalizeName(text).equals(context.session.fullName())));
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
                Ui.label("Ad soyad", "field-label"), name,
                Ui.label("Cep telefonu", "field-label"), phone,
                Ui.muted("Telefon numarası değiştirilemez: parolanız ve anahtarlarınız bu numaraya bağlıdır."),
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
