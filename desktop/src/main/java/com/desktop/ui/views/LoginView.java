package com.desktop.ui.views;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.PasswordTextField;
import com.desktop.api.Dto;
import com.desktop.config.AppConfig;
import com.desktop.service.AuthService;
import com.desktop.service.Session;
import com.desktop.ui.Async;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Shear;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Giriş ve kayıt tek kartta (StarterApp'in giriş şablonu, client/src/app/pages/login). "Kayıt ol"a basınca eğik
 * lacivert panel dönerek karşı tarafa geçer; formlar bulanıklaşıp yana kayarak sırayla yer değiştirir.
 * Ölçüler StarterApp'tekinin 1,2 katı, zamanlamalar aynıdır.
 */
public final class LoginView extends VBox {

    private static final double WIDTH = 900;
    private static final double HEIGHT = 540;
    private static final Duration ITEM_DURATION = Duration.seconds(0.7);
    private static final Duration ITEM_STEP = Duration.seconds(0.1);
    private static final Duration PANEL_DURATION = Duration.seconds(1.5);
    /** CSS'teki "ease". */
    private static final Interpolator EASE = Interpolator.SPLINE(0.25, 0.1, 0.25, 1);
    private static final PseudoClass RAISED = PseudoClass.getPseudoClass("raised");

    private final AuthService auth;
    private final Consumer<Session> onLoggedIn;

    private final TextField loginEmail = new CustomTextField();
    private final PasswordTextField loginPassword = new PasswordTextField();
    private final Button loginSubmit = submitButton("Giriş yap");
    private final Label loginStatus = statusLabel();

    private final TextField registerName = new CustomTextField();
    private final TextField registerEmail = new CustomTextField();
    private final ComboBox<Dto.Unit> registerUnit = new ComboBox<>();
    private final PasswordTextField registerPassword = new PasswordTextField();
    private final PasswordTextField registerRepeat = new PasswordTextField();
    private final Button registerSubmit = submitButton("Kayıt ol");
    private final Label registerStatus = statusLabel();

    /** Animasyonlu öğeler; StarterApp'teki --i (kayda geçerken) ve --j (girişe dönerken) gecikme sıraları. */
    private final List<Item> items = new ArrayList<>();
    /** 0: giriş, 1: kayıt. Birinci panel düzleşir, ikinci panel eğilir. */
    private final DoubleProperty panel1 = new SimpleDoubleProperty(0);
    private final DoubleProperty panel2 = new SimpleDoubleProperty(0);
    private VBox loginBox;
    private VBox registerBox;
    private ParallelTransition running;

    public LoginView(AuthService auth, Consumer<Session> onLoggedIn) {
        this.auth = auth;
        this.onLoggedIn = onLoggedIn;

        Pane card = card();
        VBox.setMargin(card, new Insets(26, 0, 22, 0));
        getChildren().addAll(brand(), card,
                Ui.label("Sadece kurum içi kullanım içindir · Sürüm " + AppConfig.get().version(), "auth-footnote"));
        getStyleClass().add("auth-page");
        setAlignment(Pos.CENTER);

        apply(false);
        loadUnits();
    }

    private static HBox brand() {
        Node logo;
        var stream = LoginView.class.getResourceAsStream("/com/desktop/icon.png");
        if (stream != null) {
            ImageView image = new ImageView(new Image(stream));
            image.setFitHeight(44);
            image.setPreserveRatio(true);
            image.setSmooth(true);
            logo = image;
        } else {
            logo = Ui.icon(Material2OutlinedMZ.VERIFIED_USER, 32);
        }
        HBox brand = Ui.row(12, logo, Ui.column(0, Ui.label("Güvenli Paylaşım", "auth-brand-title"),
                Ui.label("Kurum içi uçtan uca şifreli belge paylaşımı", "auth-brand-subtitle")));
        brand.setAlignment(Pos.CENTER);
        return brand;
    }

    // ---------- Kart ve paneller ----------

    private Pane card() {
        Pane card = new Pane();
        card.getStyleClass().add("auth-card");
        card.setMinSize(WIDTH, HEIGHT);
        card.setPrefSize(WIDTH, HEIGHT);
        card.setMaxSize(WIDTH, HEIGHT);
        Rectangle clip = new Rectangle(WIDTH, HEIGHT);
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        card.setClip(clip);

        loginBox = loginForm();
        registerBox = registerForm();
        VBox loginInfo = info(true, "HOŞ GELDİNİZ!",
                "Belgeleriniz bu bilgisayardan çıkmadan şifrelenir ve imzalanır. Sunucu dahil hiç kimse içeriği "
                        + "göremez.", 0, 20);
        VBox registerInfo = info(false, "ARAMIZA KATILIN!",
                "Anahtarlarınız bu bilgisayarda üretilir ve parolanızla korunur. Parolanızı unutursanız eski "
                        + "belgeleriniz açılamaz.", 17, 0);

        place(loginBox, 0, new Insets(0, 72, 0, 48));
        place(registerBox, WIDTH / 2, new Insets(0, 48, 0, 72));
        place(loginInfo, WIDTH / 2, new Insets(0, 48, 72, 150));
        place(registerInfo, 0, new Insets(0, 150, 72, 48));
        registerInfo.setMouseTransparent(true);
        loginInfo.setMouseTransparent(true);

        card.getChildren().addAll(rotatingPanel(), sweepingPanel(), loginBox, loginInfo, registerBox, registerInfo);
        return card;
    }

    private static void place(VBox box, double x, Insets padding) {
        box.setLayoutX(x);
        box.setPrefSize(WIDTH / 2, HEIGHT);
        box.setPadding(padding);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setFillWidth(true);
    }

    /** CSS: .rotate-bg — girişte eğik durup sağ yarıyı kaplar, kayıtta düzleşip bütün kartı kaplar. */
    private Rectangle rotatingPanel() {
        double width = 1020;
        double height = 720;
        Rectangle panel = new Rectangle(width, height);
        panel.getStyleClass().add("auth-panel");
        panel.setLayoutX(WIDTH - width);
        panel.setLayoutY(-5);
        Rotate rotate = new Rotate(0, width, height);
        Shear shear = new Shear(0, 0, width, height);
        panel.getTransforms().addAll(rotate, shear);
        Runnable update = () -> {
            double t = 1 - panel1.get();
            rotate.setAngle(10 * t);
            shear.setY(Math.tan(Math.toRadians(40 * t)));
        };
        panel1.addListener(obs -> update.run());
        update.run();
        return panel;
    }

    /** CSS: .rotate-bg2 — girişte kartın altında bekler, kayıtta dönerek sağ yarıyı kart rengiyle kaplar. */
    private Rectangle sweepingPanel() {
        double width = 1020;
        double height = 840;
        Rectangle panel = new Rectangle(width, height);
        panel.getStyleClass().add("auth-panel-light");
        panel.setLayoutX(300);
        panel.setLayoutY(HEIGHT);
        Rotate rotate = new Rotate(0, 0, height);
        Shear shear = new Shear(0, 0, 0, height);
        panel.getTransforms().addAll(rotate, shear);
        Runnable update = () -> {
            double t = panel2.get();
            rotate.setAngle(-11 * t);
            shear.setY(Math.tan(Math.toRadians(-40 * t)));
        };
        panel2.addListener(obs -> update.run());
        update.run();
        return panel;
    }

    private VBox info(boolean loginSide, String title, String text, int i, int j) {
        Label heading = Ui.label(title, "auth-info-title");
        Label body = Ui.label(text, "auth-info-text");
        for (Label label : List.of(heading, body)) {
            label.setWrapText(true);
            label.setMinHeight(Region.USE_PREF_SIZE);
        }
        if (loginSide) {
            heading.setTextAlignment(TextAlignment.RIGHT);
            body.setTextAlignment(TextAlignment.RIGHT);
        }
        VBox box = new VBox(10, heading, body);
        box.setAlignment(loginSide ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        // Giriş tarafının bilgisi sağa 120px kayarak, kayıt tarafınınki sola genişliğinin %120'si kadar kayarak gizlenir.
        animate(heading, !loginSide, i, j, loginSide ? 0 : -1.2, loginSide ? 120 : 0);
        animate(body, !loginSide, i + 1, j + 1, loginSide ? 0 : -1.2, loginSide ? 120 : 0);
        VBox wrapper = new VBox(box);
        wrapper.setAlignment(loginSide ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return wrapper;
    }

    // ---------- Formlar ----------

    private VBox loginForm() {
        loginPassword.setRight(revealIcon(loginPassword));
        loginSubmit.setOnAction(e -> login());
        loginSubmit.setDefaultButton(true);

        Node title = title("Giriş yap");
        Node email = floating("E-posta", loginEmail, Material2OutlinedMZ.MAIL_OUTLINE);
        Node password = floating("Parola", loginPassword, null);
        VBox action = new VBox(10, loginStatus, loginSubmit);
        Node link = switchLink("Hesabınız yok mu?", "Kayıt ol", true);

        animate(title, false, 0, 21, -1.2, 0);
        animate(email, false, 1, 22, -1.2, 0);
        animate(password, false, 2, 23, -1.2, 0);
        animate(action, false, 3, 24, -1.2, 0);
        animate(link, false, 5, 25, -1.2, 0);
        return new VBox(18, title, email, password, action, link);
    }

    private VBox registerForm() {
        registerPassword.setRight(revealIcon(registerPassword));
        registerRepeat.setRight(revealIcon(registerRepeat));
        registerUnit.setMaxWidth(Double.MAX_VALUE);
        registerSubmit.setOnAction(e -> register());

        Node title = title("Kayıt ol");
        Node name = floating("Ad soyad", registerName, Material2OutlinedMZ.PERSON_OUTLINE);
        Node email = floating("E-posta", registerEmail, Material2OutlinedMZ.MAIL_OUTLINE);
        Node unit = floating("Birim", registerUnit, null);
        Node password = floating("Parola (en az 10 karakter, harf ve rakam)", registerPassword, null);
        Node repeat = floating("Parola (tekrar)", registerRepeat, null);
        VBox action = new VBox(10, registerStatus, registerSubmit);
        Node link = switchLink("Hesabınız var mı?", "Giriş yap", false);

        Node[] nodes = {title, name, email, unit, password, repeat, action, link};
        for (int k = 0; k < nodes.length; k++) {
            animate(nodes[k], true, 17 + k, k, 1.2, 0);
        }
        return new VBox(10, nodes);
    }

    private static Node title(String text) {
        Label label = Ui.label(text, "auth-title");
        Region bar = new Region();
        bar.getStyleClass().add("auth-title-bar");
        VBox title = new VBox(8, label, bar);
        title.setAlignment(Pos.CENTER);
        VBox.setMargin(title, new Insets(0, 0, 8, 0));
        return title;
    }

    /** CSS: .input-box — alt çizgili alan; etiket alan boşken içinde durur, odakta ya da doluyken yukarı çıkar. */
    private static Node floating(String text, Control control, Ikon ikon) {
        control.getStyleClass().add("auth-input");
        control.setMaxWidth(Double.MAX_VALUE);
        if (ikon != null && control instanceof CustomTextField field) {
            field.setRight(Ui.icon(ikon, 18));
        }
        Label label = Ui.label(text, "auth-input-label");
        label.setMouseTransparent(true);
        label.setManaged(false);

        // Dinleyiciler doğrudan özelliklere bağlanır: ara bir BooleanBinding'e kimse güçlü referans tutmaz ve
        // çöp toplayıcı onu dinleyicisiyle birlikte siler.
        Observable value;
        BooleanSupplier filled;
        switch (control) {
            case TextField field -> {
                value = field.textProperty();
                filled = () -> !field.getText().isEmpty();
            }
            case ComboBox<?> combo -> {
                value = combo.valueProperty();
                filled = () -> combo.getValue() != null;
            }
            default -> throw new IllegalArgumentException(control.getClass().getName());
        }

        Pane box = new Pane(control, label) {
            @Override
            protected void layoutChildren() {
                control.resizeRelocate(0, 14, getWidth(), 40);
                label.autosize();
                label.relocate(2, 14 + (40 - label.getHeight()) / 2);
            }
        };
        box.setPrefHeight(56);
        box.setMinHeight(56);

        Timeline[] move = {null};
        boolean[] raised = {false};
        InvalidationListener update = obs -> {
            boolean up = control.isFocused() || filled.getAsBoolean();
            if (up == raised[0]) {
                return;
            }
            raised[0] = up;
            control.pseudoClassStateChanged(RAISED, up);
            label.pseudoClassStateChanged(RAISED, up);
            if (move[0] != null) {
                move[0].stop();
            }
            double scale = up ? 0.85 : 1;
            move[0] = new Timeline(new KeyFrame(Duration.seconds(0.3),
                    new KeyValue(label.translateYProperty(), up ? -26 : 0, EASE),
                    new KeyValue(label.translateXProperty(), -(1 - scale) * label.getWidth() / 2, EASE),
                    new KeyValue(label.scaleXProperty(), scale, EASE),
                    new KeyValue(label.scaleYProperty(), scale, EASE)));
            move[0].play();
        };
        control.focusedProperty().addListener(update);
        value.addListener(update);
        return box;
    }

    private static FontIcon revealIcon(PasswordTextField field) {
        FontIcon eye = Ui.icon(Material2OutlinedMZ.REMOVE_RED_EYE, 18);
        eye.setCursor(Cursor.HAND);
        eye.setOnMouseClicked(e -> field.setRevealPassword(!field.getRevealPassword()));
        return eye;
    }

    private Node switchLink(String question, String action, boolean toRegister) {
        Hyperlink link = new Hyperlink(action);
        link.getStyleClass().add("auth-link");
        link.setOnAction(e -> switchTo(toRegister));
        HBox row = new HBox(4, Ui.label(question, "auth-link-text"), link);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Button submitButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("auth-submit");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private static Label statusLabel() {
        Label label = new Label();
        label.setWrapText(true);
        label.setManaged(false);
        label.setVisible(false);
        return label;
    }

    // ---------- Geçiş animasyonu ----------

    private record Item(Node node, GaussianBlur blur, boolean registerSide, int i, int j,
                        double shiftWidths, double shiftPixels) {

        double hiddenX() {
            return shiftWidths * node.getLayoutBounds().getWidth() + shiftPixels;
        }
    }

    private void animate(Node node, boolean registerSide, int i, int j, double shiftWidths, double shiftPixels) {
        GaussianBlur blur = new GaussianBlur(0);
        node.setEffect(blur);
        items.add(new Item(node, blur, registerSide, i, j, shiftWidths, shiftPixels));
    }

    /** Başlangıç durumu: animasyonsuz. Gizli öğeler saydamdır; yana kayma ilk geçişte genişlikleriyle hesaplanır. */
    private void apply(boolean register) {
        for (Item item : items) {
            boolean visible = item.registerSide() == register;
            item.node().setOpacity(visible ? 1 : 0);
            item.blur().setRadius(visible ? 0 : 10);
        }
        panel1.set(register ? 1 : 0);
        panel2.set(register ? 1 : 0);
        loginBox.setDisable(register);
        registerBox.setDisable(!register);
    }

    private void switchTo(boolean register) {
        clearForms();
        if (running != null) {
            running.stop();
        }
        ParallelTransition all = new ParallelTransition();
        for (Item item : items) {
            boolean visible = item.registerSide() == register;
            if (visible && item.node().getOpacity() == 0) {
                item.node().setTranslateX(item.hiddenX());
            }
            Timeline timeline = new Timeline(new KeyFrame(ITEM_DURATION,
                    new KeyValue(item.node().opacityProperty(), visible ? 1 : 0, EASE),
                    new KeyValue(item.blur().radiusProperty(), visible ? 0 : 10, EASE),
                    new KeyValue(item.node().translateXProperty(), visible ? 0 : item.hiddenX(), EASE)));
            timeline.setDelay(ITEM_STEP.multiply(register ? item.i() : item.j()));
            all.getChildren().add(timeline);
        }
        all.getChildren().add(panelTimeline(panel1, register ? 1 : 0, register ? 0.5 : 1.6));
        all.getChildren().add(panelTimeline(panel2, register ? 1 : 0, register ? 1.2 : 0.5));

        // Gizlenen form hemen, görünen form animasyon başlarken etkileşime açılır (StarterApp'te [inert]).
        loginBox.setDisable(register);
        registerBox.setDisable(!register);
        loginSubmit.setDefaultButton(!register);
        registerSubmit.setDefaultButton(register);
        running = all;
        all.play();
    }

    private static Timeline panelTimeline(DoubleProperty progress, double target, double delaySeconds) {
        Timeline timeline = new Timeline(new KeyFrame(PANEL_DURATION, new KeyValue(progress, target, EASE)));
        timeline.setDelay(Duration.seconds(delaySeconds));
        return timeline;
    }

    private void clearForms() {
        loginEmail.clear();
        loginPassword.clear();
        registerName.clear();
        registerEmail.clear();
        registerUnit.setValue(null);
        registerPassword.clear();
        registerRepeat.clear();
        showStatus(loginStatus, null, false);
        showStatus(registerStatus, null, false);
    }

    // ---------- İşlemler ----------

    private void loadUnits() {
        Async.run(auth::units, units -> registerUnit.getItems().setAll(units),
                failure -> showStatus(registerStatus, "Birimler alınamadı: " + Toasts.describe(failure), true));
    }

    private void login() {
        if (loginEmail.getText().isBlank() || loginPassword.getPassword().isEmpty()) {
            showStatus(loginStatus, "E-posta ve parola zorunludur.", true);
            return;
        }
        busy(loginSubmit, loginStatus, "Parolanızdan anahtar türetiliyor ve anahtarlarınız açılıyor…");
        Async.run(() -> auth.login(loginEmail.getText(), loginPassword.getPassword().toCharArray()),
                session -> {
                    loginPassword.clear();
                    onLoggedIn.accept(session);
                },
                failure -> failed(loginSubmit, loginStatus, failure));
    }

    private void register() {
        String error = validateRegister();
        if (error != null) {
            showStatus(registerStatus, error, true);
            return;
        }
        busy(registerSubmit, registerStatus, "Hesabınız açılıyor ve anahtarlarınız bu bilgisayarda üretiliyor…");
        Async.run(() -> auth.register(registerName.getText(), registerEmail.getText(), registerUnit.getValue().id(),
                        registerPassword.getPassword().toCharArray()),
                session -> {
                    registerPassword.clear();
                    registerRepeat.clear();
                    onLoggedIn.accept(session);
                },
                failure -> failed(registerSubmit, registerStatus, failure));
    }

    private String validateRegister() {
        if (registerName.getText().isBlank()) {
            return "Ad soyad zorunludur.";
        }
        if (registerEmail.getText().isBlank()) {
            return "E-posta zorunludur.";
        }
        if (registerUnit.getValue() == null) {
            return "Birim seçilmelidir.";
        }
        return AuthService.validateNewPassword(registerPassword.getPassword().toCharArray(),
                registerRepeat.getPassword().toCharArray());
    }

    private static void busy(Button submit, Label status, String message) {
        submit.setDisable(true);
        showStatus(status, message, false);
    }

    private static void failed(Button submit, Label status, Throwable failure) {
        submit.setDisable(false);
        showStatus(status, Toasts.describe(failure), true);
    }

    private static void showStatus(Label status, String message, boolean error) {
        status.setText(message == null ? "" : message);
        status.getStyleClass().removeAll("form-error", "auth-progress");
        status.getStyleClass().add(error ? "form-error" : "auth-progress");
        status.setVisible(message != null);
        status.setManaged(message != null);
    }
}
