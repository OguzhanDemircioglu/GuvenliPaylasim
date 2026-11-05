package com.desktop;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.desktop.config.AppConfig;
import com.desktop.service.AuthService;
import com.desktop.service.Session;
import com.desktop.ui.Overlays;
import com.desktop.ui.views.LoginView;
import com.desktop.ui.views.PasswordSetupView;
import com.desktop.ui.views.ShellView;
import com.desktop.update.UpdateGate;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Ekran akışı: güncelleme kontrolü → giriş → (gerekirse ilk parola) → ana ekran.
 */
public class App extends Application {

    private static final Preferences PREFS = Preferences.userNodeForPackage(App.class);
    private static final String PREF_DARK = "theme.dark";

    private final StackPane content = new StackPane();
    private final AuthService auth = new AuthService(AppConfig.get().serverUrl());
    private Session session;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        applyTheme(PREFS.getBoolean(PREF_DARK, false));

        ModalPane modal = new ModalPane();
        VBox toastLayer = new VBox();
        StackPane root = new StackPane(content, modal, toastLayer);
        Overlays.install(modal, toastLayer);

        Scene scene = new Scene(root, 1320, 840);
        scene.getStylesheets().add(App.class.getResource("/com/desktop/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Güvenli Paylaşım");
        stage.setMinWidth(1120);
        stage.setMinHeight(720);
        stage.getIcons().addAll(windowIcons());
        stage.show();

        show(new UpdateGate(this::showLogin));
    }

    /**
     * Başlık çubuğu ve görev çubuğu için hazır boyutlar: Windows en uygununu seçer. Tek büyük görsel 16 px'e
     * küçültülünce bulanıklaşır.
     */
    static List<Image> windowIcons() {
        List<Image> icons = new ArrayList<>();
        for (String name : List.of("icon-16", "icon-24", "icon-32", "icon-48", "icon-64", "icon-128", "icon")) {
            var stream = App.class.getResourceAsStream("/com/desktop/" + name + ".png");
            if (stream != null) {
                icons.add(new Image(stream));
            }
        }
        return icons;
    }

    @Override
    public void stop() {
        if (session != null) {
            auth.logout(session);
        }
    }

    public void showLogin() {
        session = null;
        show(new LoginView(auth, this::onLoggedIn));
    }

    private void onLoggedIn(Session newSession) {
        session = newSession;
        if (newSession.user().mustChangePassword()) {
            show(new PasswordSetupView(auth, newSession, () -> showShell(newSession), this::logout));
        } else {
            showShell(newSession);
        }
    }

    private void showShell(Session active) {
        show(new ShellView(this, auth, active));
    }

    public void logout() {
        if (session != null) {
            auth.logout(session);
        }
        showLogin();
    }

    public static boolean isDark() {
        return PREFS.getBoolean(PREF_DARK, false);
    }

    public static void toggleTheme() {
        boolean dark = !isDark();
        PREFS.putBoolean(PREF_DARK, dark);
        applyTheme(dark);
    }

    private static void applyTheme(boolean dark) {
        Application.setUserAgentStylesheet(dark
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }

    private void show(Node view) {
        content.getChildren().setAll(view);
    }
}
