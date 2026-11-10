package com.desktop;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.desktop.api.ApiException;
import com.desktop.config.AppConfig;
import com.desktop.service.AuthService;
import com.desktop.service.Session;
import com.desktop.ui.Async;
import com.desktop.ui.Overlays;
import com.desktop.ui.Toasts;
import com.desktop.ui.views.LoginView;
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
    /** Ana pencere: Verdana ve büyütülmüş yazıyla Gelen Kutusu'nda liste ve belge yan yana sığsın diye. */
    public static final double MAIN_WIDTH = 1440;
    public static final double MAIN_HEIGHT = 900;

    private final StackPane content = new StackPane();
    private final StackPane root = new StackPane();
    private Stage stage;
    private Boolean compact;
    private final AuthService auth = new AuthService(AppConfig.get().serverUrl());
    private Session session;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        applyTheme(PREFS.getBoolean(PREF_DARK, false));

        ModalPane modal = new ModalPane();
        VBox toastLayer = new VBox();
        root.getChildren().addAll(content, modal, toastLayer);
        Overlays.install(modal, toastLayer);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(App.class.getResource("/com/desktop/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Lockpost");
        stage.getIcons().addAll(windowIcons());
        setCompact(true);
        stage.show();

        show(new UpdateGate(this::startSession));
    }

    /**
     * "Beni hatırla" kaydı varsa parola sorulmadan ana ekrana geçilir; kayıt geçersizse giriş ekranı açık kalır.
     * Sunucuya ulaşılamazsa kayıt silinmez, kullanıcıya bildirilir.
     */
    private void startSession() {
        showLogin();
        if (!auth.hasRemembered()) {
            return;
        }
        Async.run(auth::resume,
                resumed -> resumed.ifPresent(this::onLoggedIn),
                failure -> {
                    if (failure instanceof ApiException api && api.status() == 0) {
                        Toasts.error(failure);
                    }
                });
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
        // Hatırlanan oturum açık kalır; bir sonraki açılışta kayıttaki refresh token'la devam edilir.
        if (session != null && !session.remembered()) {
            auth.logout(session);
        }
    }

    public void showLogin() {
        session = null;
        setCompact(true);
        show(new LoginView(auth, this::onLoggedIn));
    }

    private void onLoggedIn(Session newSession) {
        session = newSession;
        setCompact(false);
        showShell(newSession);
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

    /**
     * Güncelleme kontrolü ve giriş ekranında pencere sadece giriş kartı kadardır ve boyutlandırılamaz; girişten
     * sonra büyük, boyutlandırılabilir pencereye geçilir.
     */
    private void setCompact(boolean value) {
        if (compact != null && compact == value) {
            return;
        }
        compact = value;
        stage.setMaximized(false);
        stage.setMinWidth(0);
        stage.setMinHeight(0);
        stage.setResizable(!value);
        root.setPrefSize(value ? LoginView.WIDTH : MAIN_WIDTH, value ? LoginView.HEIGHT : MAIN_HEIGHT);
        stage.sizeToScene();
        stage.centerOnScreen();
        if (!value) {
            stage.setMinWidth(1240);
            stage.setMinHeight(760);
        }
    }

    private void show(Node view) {
        content.getChildren().setAll(view);
    }
}
