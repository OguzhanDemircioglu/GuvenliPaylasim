package com.desktop.ui.views;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import com.desktop.config.AppConfig;
import com.desktop.service.AuthService;
import com.desktop.service.Session;
import com.desktop.ui.Async;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.function.Consumer;

/** Giriş ekranı: solda ürünün ne yaptığını anlatan panel, sağda form. */
public final class LoginView extends HBox {

    private final AuthService auth;
    private final Consumer<Session> onLoggedIn;
    private final TextField email = new TextField();
    private final PasswordTextField password = new PasswordTextField();
    private final Button submit = Ui.primary("Giriş yap", Material2OutlinedAL.LOGIN);
    private final Label error = Ui.label("", "form-error");
    private final Label progress = Ui.muted("");

    public LoginView(AuthService auth, Consumer<Session> onLoggedIn) {
        this.auth = auth;
        this.onLoggedIn = onLoggedIn;

        VBox hero = hero();
        HBox.setHgrow(hero, Priority.ALWAYS);
        StackPane panel = new StackPane(form());
        panel.getStyleClass().add("login-panel");
        panel.setMinWidth(500);
        panel.setPrefWidth(540);
        getChildren().addAll(hero, panel);
    }

    private VBox hero() {
        StackPane mark = new StackPane(Ui.icon(Material2OutlinedMZ.VERIFIED_USER, 22));
        mark.getStyleClass().add("brand-mark");
        HBox brand = Ui.row(12, mark, Ui.column(0, Ui.label("Güvenli Paylaşım", "brand-title"),
                Ui.label("Kurum içi belge paylaşım sistemi", "brand-subtitle")));

        Label title = Ui.label("Gizli belgeler,\nsadece muhatabına.", "hero-title");
        Label text = Ui.label("Belgeleriniz bu bilgisayardan çıkmadan şifrelenir ve imzalanır. Sunucu dahil hiç "
                + "kimse içeriği göremez; sadece seçtiğiniz kişiler açabilir.", "hero-text");
        text.setWrapText(true);
        text.setMaxWidth(470);

        VBox features = Ui.column(18,
                feature(Material2OutlinedAL.ENHANCED_ENCRYPTION, "Uçtan uca şifreleme",
                        "X25519 + AES-256-GCM. Anahtarınız bilgisayarınızda üretilir."),
                feature(Material2OutlinedMZ.VERIFIED_USER, "Dijital imza",
                        "Her belge Ed25519 ile imzalanır; değiştirilirse açılmaz."),
                feature(Material2OutlinedMZ.SMS, "SMS ile parola yok",
                        "Paylaşılacak bir parola olmadığı için ele geçirilecek bir şey de yok."));

        VBox hero = new VBox(brand, Ui.grow(), Ui.column(18, title, text), new VBox(), features, Ui.grow(),
                Ui.label("Sadece kurum içi kullanım içindir · Sürüm " + AppConfig.get().version(), "hero-footnote"));
        VBox.setMargin(features, new Insets(36, 0, 0, 0));
        hero.getStyleClass().add("login-hero");
        return hero;
    }

    private static HBox feature(Ikon ikon, String title, String text) {
        StackPane icon = new StackPane(Ui.icon(ikon, 20));
        icon.getStyleClass().add("hero-feature-icon");
        Label body = Ui.label(text, "hero-feature-text");
        body.setWrapText(true);
        return Ui.row(14, icon, Ui.column(2, Ui.label(title, "hero-feature-title"), body));
    }

    private VBox form() {
        email.setPromptText("ad.soyad@kurum.gov.tr");
        password.setPromptText("Parolanız");
        FontIcon eye = Ui.icon(Material2OutlinedMZ.REMOVE_RED_EYE, 18);
        eye.setCursor(Cursor.HAND);
        eye.setOnMouseClicked(e -> password.setRevealPassword(!password.getRevealPassword()));
        password.setRight(eye);

        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);
        submit.setOnAction(e -> login());
        error.setWrapText(true);
        error.setManaged(false);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(16, 16);
        HBox working = Ui.row(8, spinner, progress);
        working.visibleProperty().bind(submit.disableProperty());
        working.managedProperty().bind(working.visibleProperty());

        VBox form = new VBox(
                Ui.label("Hoş geldiniz", Styles.TITLE_1),
                Ui.muted("Kurum hesabınızla giriş yapın."),
                gap(26),
                Ui.label("E-posta", "field-label"), gap(6), email, gap(16),
                Ui.label("Parola", "field-label"), gap(6), password, gap(10),
                error, gap(14), submit, gap(14), working);
        form.getStyleClass().add("login-form");
        form.setMaxWidth(360);
        form.setAlignment(Pos.CENTER_LEFT);
        form.setFillWidth(true);
        StackPane.setAlignment(form, Pos.CENTER);
        return form;
    }

    private void login() {
        if (email.getText().isBlank() || password.getPassword().isEmpty()) {
            showError("E-posta ve parola zorunludur.");
            return;
        }
        showError(null);
        submit.setDisable(true);
        progress.setText("Parolanızdan anahtar türetiliyor ve anahtarlarınız açılıyor…");
        Async.run(() -> auth.login(email.getText(), password.getPassword().toCharArray()),
                session -> {
                    password.clear();
                    onLoggedIn.accept(session);
                },
                failure -> {
                    submit.setDisable(false);
                    showError(Toasts.describe(failure));
                });
    }

    private void showError(String message) {
        error.setText(message == null ? "" : message);
        error.setManaged(message != null);
    }

    private static VBox gap(double height) {
        VBox gap = new VBox();
        gap.setMinHeight(height);
        return gap;
    }
}
