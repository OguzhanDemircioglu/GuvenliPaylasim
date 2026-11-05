package com.desktop.ui.views;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.desktop.crypto.UserKeys;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

/** Kullanıcının anahtarları: parmak izi, kullanılan algoritmalar ve parola değiştirme. */
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

        VBox left = new VBox(20, fingerprintCard, algorithmCard);
        HBox.setHgrow(left, Priority.ALWAYS);
        passwordCard.setPrefWidth(420);
        passwordCard.setMinWidth(380);
        HBox body = new HBox(20, left, passwordCard);

        VBox page = new VBox(Ui.header("Anahtarım", "Kimliğinizi kanıtlayan ve belgelerinizi açan anahtarlar"), body);
        page.getStyleClass().add("page");
        setContent(page);
        setFitToWidth(true);
        getStyleClass().add(Tweaks.EDGE_TO_EDGE);
        setStyle("-fx-background-color: transparent; -fx-background: transparent;");
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
