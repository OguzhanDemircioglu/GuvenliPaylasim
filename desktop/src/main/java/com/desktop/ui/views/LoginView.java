package com.desktop.ui.views;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.PasswordTextField;
import com.desktop.api.ApiException;
import com.desktop.config.AppConfig;
import com.desktop.service.AuthService;
import com.desktop.service.PasswordStrength;
import com.desktop.service.Phones;
import com.desktop.service.Session;
import com.desktop.ui.Async;
import com.desktop.ui.Cooldown;
import com.desktop.ui.Dialogs;
import com.desktop.ui.Inputs;
import com.desktop.ui.PasswordFields;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.GaussianBlur;
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
 * Giriş ve kayıt tek kartta (StarterApp'in giriş şablonu, client/src/app/pages/login). "Kayıt Ol"a basınca eğik
 * lacivert panel dönerek karşı tarafa geçer; formlar bulanıklaşıp yana kayarak sırayla yer değiştirir.
 * Ölçüler StarterApp'tekinin 1,2 katı, zamanlamalar aynıdır.
 */
public final class LoginView extends VBox {

    public static final double WIDTH = 1000;
    public static final double HEIGHT = 600;
    /** Bilgi yazılarının ve sürüm etiketinin kartın dış kenarına uzaklığı. */
    private static final double INFO_EDGE = 48;
    /** Paneller ilk 900 px genişliğindeki karta göre çizildi; kart büyüyünce aynı oranda büyür. */
    private static final double SCALE = WIDTH / 900;
    private static final Duration ITEM_DURATION = Duration.seconds(0.7);
    private static final Duration ITEM_STEP = Duration.seconds(0.1);
    private static final Duration PANEL_DURATION = Duration.seconds(1.5);
    /** CSS'teki "ease". */
    private static final Interpolator EASE = Interpolator.ofSpline(0.25, 0.1, 0.25, 1);
    private static final PseudoClass RAISED = PseudoClass.getPseudoClass("raised");

    private final AuthService auth;
    private final Consumer<Session> onLoggedIn;

    private final TextField loginPhone = Inputs.phone(new CustomTextField());
    private final PasswordTextField loginPassword = new PasswordTextField();
    private final CheckBox rememberMe = new CheckBox("Bu Cihaza Güven (30 gün)");
    private final CheckBox trustDevice = new CheckBox("Bu Cihaza Güven (30 gün)");
    private final CheckBox noticeRead = new CheckBox();
    private final Button loginSubmit = submitButton("Giriş Yap");
    private final Label loginStatus = statusLabel();

    private final TextField registerName = Inputs.name(new CustomTextField());
    private final TextField registerPhone = Inputs.phone(new CustomTextField());
    private final CustomTextField registerCode = Inputs.digits(new CustomTextField(), 6);
    private final Hyperlink sendCode = new Hyperlink("Kod Gönder");
    private final PasswordTextField registerPassword = new PasswordTextField();
    private final PasswordTextField registerRepeat = new PasswordTextField();
    private final Label registerPasswordHint = PasswordFields.hint();
    private final Label registerRepeatHint = PasswordFields.hint();
    private final Button registerSubmit = submitButton("Kayıt Ol");
    private final Label registerStatus = statusLabel();

    /** Animasyonlu öğeler; StarterApp'teki --i (kayda geçerken) ve --j (girişe dönerken) gecikme sıraları. */
    private final List<Item> items = new ArrayList<>();
    /** 0: giriş, 1: kayıt. Birinci panel düzleşir, ikinci panel eğilir. */
    private final DoubleProperty panel1 = new SimpleDoubleProperty(0);
    private final DoubleProperty panel2 = new SimpleDoubleProperty(0);
    private VBox loginBox;
    private VBox registerBox;
    private ParallelTransition running;
    private final HBox versionRow = new HBox();

    public LoginView(AuthService auth, Consumer<Session> onLoggedIn) {
        this.auth = auth;
        this.onLoggedIn = onLoggedIn;

        getChildren().add(card());
        setAlignment(Pos.CENTER);
        bindButtons();

        apply(false);
    }

    // ---------- Düğmelerin açık/kapalı durumu ----------

    /**
     * Giriş: telefon 10 hane ve parola yazılmadan "Giriş Yap" kapalı. Kayıt: form eksiksiz ve geçerli olmadan
     * "Kod Gönder" kapalı (geri sayımda da kapalı); WhatsApp kodu 6 hane olmadan "Kayıt Ol" kapalı. İşlem sürerken
     * düğme meşguldür ({@link #busyFlag}).
     */
    private void bindButtons() {
        BooleanBinding loginReady = Bindings.createBooleanBinding(
                () -> Phones.normalize(loginPhone.getText()) != null
                        && PasswordStrength.composition(loginPassword.getPassword()) == null,
                loginPhone.textProperty(), loginPassword.passwordProperty());
        // Girişte uyarı gösterilmez (docs/UI_Standards.md); kural tutmazsa sadece düğme kapalı kalır.
        loginSubmit.disableProperty().bind(busyFlag(loginSubmit).or(loginReady.not()));
        PasswordFields.limit(loginPassword);

        // Kayıt: ilk şifrenin altında kural uyarısı, tekrarın altında "Şifreler uyuşmuyor." (eşleşene kadar).
        PasswordFields.limit(registerPassword);
        PasswordFields.limit(registerRepeat);
        PasswordFields.bindHints(registerPassword, registerRepeat, registerPasswordHint, registerRepeatHint,
                password -> PasswordStrength.check(password, registerName.getText(), registerPhone.getText()));

        BooleanBinding formReady = Bindings.createBooleanBinding(() -> registerFormProblem() == null,
                registerName.textProperty(), registerPhone.textProperty(), registerPassword.passwordProperty(),
                registerRepeat.passwordProperty(), noticeRead.selectedProperty());
        sendCode.disableProperty().bind(busyFlag(sendCode).or(Cooldown.running(sendCode)).or(formReady.not()));

        BooleanBinding codeReady = Bindings.createBooleanBinding(() -> validCode(registerCode.getText()),
                registerCode.textProperty());
        registerSubmit.disableProperty().bind(busyFlag(registerSubmit).or(formReady.not()).or(codeReady.not()));

        // Form doldurulmuş ama ad, telefon ya da onay eksikse neden kod gönderilemediği yazılır. Şifre sorunları
        // kendi alanlarının altında gösterildiği için burada tekrar edilmez.
        formReady.addListener((obs, old, ready) -> showFormHint());
        for (Observable field : List.of(registerName.textProperty(), registerPhone.textProperty(),
                registerPassword.passwordProperty(), registerRepeat.passwordProperty(), noticeRead.selectedProperty())) {
            field.addListener(obs -> showFormHint());
        }
    }

    private String formHint;

    private void showFormHint() {
        boolean filled = !registerName.getText().isBlank() && !registerPhone.getText().isBlank()
                && !registerPassword.getPassword().isEmpty() && !registerRepeat.getPassword().isEmpty();
        String problem = filled ? registerOtherProblem() : null;
        boolean showingHint = formHint != null && formHint.equals(registerStatus.getText());
        if (problem != null) {
            formHint = problem;
            showStatus(registerStatus, problem, true);
        } else if (showingHint) {
            formHint = null;
            showStatus(registerStatus, null, false);
        }
    }

    /** İşlem sürüyor mu (giriş, kod gönderme, kayıt)? Düğmenin kapalılığı buna ve formun durumuna bağlıdır. */
    private static BooleanProperty busyFlag(ButtonBase button) {
        return (BooleanProperty) button.getProperties().computeIfAbsent("busy", k -> new SimpleBooleanProperty(false));
    }

    // ---------- Kart ve paneller ----------

    private Pane card() {
        Pane card = new Pane();
        card.getStyleClass().add("auth-card");
        card.setMinSize(WIDTH, HEIGHT);
        card.setPrefSize(WIDTH, HEIGHT);
        card.setMaxSize(WIDTH, HEIGHT);
        Rectangle clip = new Rectangle(WIDTH, HEIGHT);
        card.setClip(clip);

        loginBox = loginForm();
        registerBox = registerForm();
        VBox loginInfo = info(true, "HOŞ GELDİNİZ!",
                "Belgeleriniz bu bilgisayardan çıkmadan şifrelenir ve imzalanır. Sunucu dahil hiç kimse içeriği "
                        + "göremez.", 0, 20);
        VBox registerInfo = info(false, "ARAMIZA KATILIN!",
                "Telefonunuz WhatsApp koduyla doğrulanır. Anahtarlarınız bu bilgisayarda üretilir; şifrenizi "
                        + "unutursanız size gelmiş belgeler açılamaz.", 17, 0);

        place(loginBox, 0, new Insets(0, 72, 0, 48));
        place(registerBox, WIDTH / 2, new Insets(0, 48, 0, 72));
        place(loginInfo, WIDTH / 2, new Insets(0, INFO_EDGE, 72, 110));
        place(registerInfo, 0, new Insets(0, 110, 72, INFO_EDGE));
        registerInfo.setMouseTransparent(true);
        loginInfo.setMouseTransparent(true);

        versionRow.getChildren().add(Ui.label("v" + AppConfig.get().version(), "auth-version"));
        versionRow.setPrefWidth(WIDTH);
        // Kenar boşluğu bilgi yazılarıyla aynı (INFO_EDGE): girişte sağ kenar "HOŞ GELDİNİZ!", kayıtta sol kenar
        // "ARAMIZA KATILIN!" ile aynı hizada. Lacivert panel köşede ~150 px; yazı onun içinde kalır.
        versionRow.setLayoutY(HEIGHT - 40);
        versionRow.setPadding(new Insets(0, INFO_EDGE, 0, INFO_EDGE));
        versionRow.setMouseTransparent(true);

        card.getChildren().addAll(rotatingPanel(), sweepingPanel(), loginBox, loginInfo, registerBox, registerInfo,
                versionRow);
        return card;
    }

    /**
     * Sürüm lacivert panelin köşesinde, beyaz: girişte sağ altta, kayıtta sol altta. Ekranlar arası geçiş sırasında
     * görünmez.
     */
    private void placeVersion(boolean register) {
        versionRow.setAlignment(register ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
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
        double width = 1020 * SCALE;
        double height = 720 * SCALE;
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
        double width = 1020 * SCALE;
        double height = 840 * SCALE;
        Rectangle panel = new Rectangle(width, height);
        panel.getStyleClass().add("auth-panel-light");
        panel.setLayoutX(300 * SCALE);
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

        Node title = title("Giriş Yap");
        Node phone = floating("Cep Telefonu (5xx) xxx xx xx", loginPhone, Material2OutlinedMZ.PHONE_ANDROID);
        Node password = floating("Şifre", loginPassword, null);
        Hyperlink forgot = new Hyperlink("Şifremi Unuttum");
        forgot.getStyleClass().add("auth-link");
        forgot.setOnAction(e -> openPasswordReset());
        rememberMe.getStyleClass().add("auth-check");
        rememberMe.setTooltip(new Tooltip("Bu bilgisayarda 30 gün boyunca şifre sorulmaz. Anahtarlarınız Windows "
                + "hesabınıza bağlı olarak şifreli saklanır; ortak kullanılan bilgisayarda seçmeyin."));
        HBox forgotRow = new HBox(rememberMe, Ui.grow(), forgot);
        forgotRow.setAlignment(Pos.CENTER_LEFT);
        VBox action = new VBox(10, forgotRow, loginStatus, loginSubmit);
        Node link = switchLink("Hesabınız yok mu?", "Kayıt Ol", true);

        animate(title, false, 0, 21, -1.2, 0);
        animate(phone, false, 1, 22, -1.2, 0);
        animate(password, false, 2, 23, -1.2, 0);
        animate(action, false, 3, 24, -1.2, 0);
        animate(link, false, 5, 25, -1.2, 0);
        return new VBox(18, title, phone, password, action, link);
    }

    private VBox registerForm() {
        registerPassword.setRight(revealIcon(registerPassword));
        registerRepeat.setRight(revealIcon(registerRepeat));
        registerSubmit.setOnAction(e -> register());
        sendCode.getStyleClass().add("auth-link");
        sendCode.setTooltip(new Tooltip("Telefonunuza WhatsApp ile 6 haneli kod gönderilir"));
        sendCode.setOnAction(e -> sendRegistrationCode());

        Node title = title("Kayıt Ol");
        Node name = floating("Ad Soyad", registerName, Material2OutlinedMZ.PERSON_OUTLINE);
        Node phone = floating("Cep Telefonu (5xx) xxx xx xx", registerPhone, Material2OutlinedMZ.PHONE_ANDROID);
        Node password = new VBox(floating("Şifre (12–20 karakter)", registerPassword, null), registerPasswordHint);
        Node repeat = new VBox(floating("Şifre (tekrar)", registerRepeat, null), registerRepeatHint);
        Node code = floating("WhatsApp Kodu", registerCode, null);
        registerCode.setRight(sendCode);
        trustDevice.getStyleClass().add("auth-check");
        trustDevice.setTooltip(new Tooltip("Uygulamayı kapatıp açtığınızda 30 gün boyunca şifre sorulmaz. "
                + "Anahtarlarınız Windows hesabınıza bağlı olarak şifreli saklanır; ortak kullanılan bilgisayarda "
                + "seçmeyin. Güvenli çıkış yaparsanız kayıt silinir."));
        noticeRead.getStyleClass().add("auth-check");
        noticeRead.setSelected(true);
        Hyperlink notice = new Hyperlink("Aydınlatma Metnini");
        notice.getStyleClass().add("auth-link");
        notice.setOnAction(e -> showNotice());
        Label noticeText = Ui.label("Onaylıyorum", "auth-link-text");
        noticeText.setOnMouseClicked(e -> noticeRead.setSelected(!noticeRead.isSelected()));
        HBox noticeRow = new HBox(4, noticeRead, notice, noticeText);
        noticeRow.setAlignment(Pos.CENTER_LEFT);
        VBox checks = new VBox(6, noticeRow, trustDevice);
        VBox action = new VBox(8, checks, registerStatus, registerSubmit);
        Node link = switchLink("Hesabınız var mı?", "Giriş Yap", false);

        Node[] nodes = {title, name, phone, password, repeat, code, action, link};
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
        placeVersion(register);
        versionRow.setOpacity(1);
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
        versionRow.setOpacity(0);
        all.setOnFinished(e -> {
            placeVersion(register);
            Timeline show = new Timeline(new KeyFrame(Duration.millis(250),
                    new KeyValue(versionRow.opacityProperty(), 1, EASE)));
            show.play();
        });
        running = all;
        all.play();
    }

    private static Timeline panelTimeline(DoubleProperty progress, double target, double delaySeconds) {
        Timeline timeline = new Timeline(new KeyFrame(PANEL_DURATION, new KeyValue(progress, target, EASE)));
        timeline.setDelay(Duration.seconds(delaySeconds));
        return timeline;
    }

    private void clearForms() {
        loginPhone.clear();
        loginPassword.clear();
        registerName.clear();
        registerPhone.clear();
        registerCode.clear();
        registerPassword.clear();
        registerRepeat.clear();
        showStatus(loginStatus, null, false);
        showStatus(registerStatus, null, false);
    }

    // ---------- İşlemler ----------

    private void login() {
        String phoneError = AuthService.validatePhone(loginPhone.getText());
        if (phoneError != null || loginPassword.getPassword().isEmpty()) {
            showStatus(loginStatus, phoneError != null ? phoneError : "Şifre zorunludur.", true);
            return;
        }
        loginWith(null);
    }

    /** TOTP açık hesapta sunucu önce kodu ister ({@code auth_totp_required}); kod sorulup giriş tekrarlanır. */
    private void loginWith(String totpCode) {
        busy(loginSubmit, loginStatus, "Şifrenizden anahtar türetiliyor ve anahtarlarınız açılıyor…");
        String phone = loginPhone.getText();
        char[] password = loginPassword.getPassword().toCharArray();
        boolean remember = rememberMe.isSelected();
        Async.run(() -> rememberOrForget(auth.login(phone, password, totpCode), remember),
                session -> {
                    loginPassword.clear();
                    onLoggedIn.accept(session);
                },
                failure -> {
                    if (isTotpRequired(failure)) {
                        busyFlag(loginSubmit).set(false);
                        showStatus(loginStatus, null, false);
                        askTotp(this::loginWith);
                    } else {
                        failed(loginSubmit, loginStatus, failure);
                    }
                });
    }

    /**
     * "Beni hatırla" (giriş) ve "Bu cihaza güven" (kayıt) aynı kaydı kullanır (RememberedLogin). Hatırlama başarısız
     * olsa da giriş geçerlidir; sadece bir sonraki açılışta parola sorulur. Arka planda çağrılır.
     */
    private Session rememberOrForget(Session session, boolean remember) {
        try {
            if (remember) {
                auth.remember(session);
            } else {
                auth.forget();
            }
        } catch (RuntimeException e) {
            auth.forget();
            Platform.runLater(() -> Toasts.info("Oturum bu bilgisayarda hatırlanamadı; bir sonraki açılışta şifre "
                    + "sorulacak."));
        }
        return session;
    }

    private static boolean isTotpRequired(Throwable failure) {
        return failure instanceof ApiException api && "auth_totp_required".equals(api.key());
    }

    /** İki adımlı doğrulama kodu: Authenticator'daki 6 hane ya da kurtarma kodlarından biri. */
    private static void askTotp(java.util.function.Consumer<String> onCode) {
        TextField code = new TextField();
        code.setPromptText("123456 ya da XXXXX-XXXXX");
        code.getStyleClass().add("code-input");
        Label help = Ui.muted("Authenticator uygulamanızdaki 6 haneli kodu yazın. Telefonunuz yanınızda değilse "
                + "kurtarma kodlarınızdan birini kullanabilirsiniz; her kurtarma kodu bir kez geçer.");
        help.setWrapText(true);
        help.setMaxWidth(420);
        Button cancel = Ui.button("Vazgeç", null);
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> Dialogs.close());
        Button submit = Ui.primary("Doğrula", Material2OutlinedAL.LOCK_OPEN);
        submit.setDefaultButton(true);
        submit.setOnAction(e -> {
            if (code.getText().isBlank()) {
                return;
            }
            Dialogs.close();
            onCode.accept(code.getText().trim());
        });
        Dialogs.show("İki Adımlı Doğrulama", new VBox(12, help, code), cancel, submit);
        code.requestFocus();
    }

    private void register() {
        String error = validateRegister();
        if (error != null) {
            showStatus(registerStatus, error, true);
            return;
        }
        busy(registerSubmit, registerStatus, "Hesabınız açılıyor ve anahtarlarınız bu bilgisayarda üretiliyor…");
        String phone = registerPhone.getText();
        String code = registerCode.getText();
        String name = Inputs.normalizeName(registerName.getText());
        char[] password = registerPassword.getPassword().toCharArray();
        boolean trust = trustDevice.isSelected();
        Async.run(() -> rememberOrForget(auth.completeRegistration(phone, code, name, password), trust),
                session -> {
                    registerPassword.clear();
                    registerRepeat.clear();
                    onLoggedIn.accept(session);
                },
                failure -> failed(registerSubmit, registerStatus, failure));
    }

    /** KVKK m.10 aydınlatma metni (taslak, resources/com/desktop/aydinlatma-metni.txt). "Onaylıyorum" kutuyu işaretler. */
    private void showNotice() {
        TextArea text = new TextArea(noticeText());
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefSize(620, 420);
        Button close = Ui.button("Kapat", null);
        close.setCancelButton(true);
        close.setOnAction(e -> Dialogs.close());
        Button read = Ui.primary("Onaylıyorum", Material2OutlinedAL.CHECK);
        read.setDefaultButton(true);
        read.setOnAction(e -> {
            noticeRead.setSelected(true);
            Dialogs.close();
        });
        Dialogs.show("Aydınlatma Metni", text, close, read);
    }

    static String noticeText() {
        try (var in = LoginView.class.getResourceAsStream("/com/desktop/aydinlatma-metni.txt")) {
            return in == null ? "Aydınlatma metni bulunamadı."
                    : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "Aydınlatma metni okunamadı.";
        }
    }

    /** Kod gönderilmeden önce form: ad, telefon, şifre kuralları ve aydınlatma metni. Sorun yoksa null. */
    private String registerFormProblem() {
        String other = registerOtherProblem();
        if (other != null && !other.equals(NOTICE_REQUIRED)) {
            return other;
        }
        String passwordError = AuthService.validateNewPassword(registerPassword.getPassword().toCharArray(),
                registerRepeat.getPassword().toCharArray(), registerName.getText(), registerPhone.getText());
        return passwordError != null ? passwordError : other;
    }

    private static final String NOTICE_REQUIRED = "Kayıt olmak için aydınlatma metnini okuyup onaylayın.";

    /** Şifre dışındaki sorunlar (ad, telefon, aydınlatma metni); şifreninkiler alanların altında gösterilir. */
    private String registerOtherProblem() {
        String nameError = Inputs.nameError(registerName.getText());
        if (nameError != null) {
            return nameError;
        }
        String phoneError = AuthService.validatePhone(registerPhone.getText());
        if (phoneError != null) {
            return phoneError;
        }
        return noticeRead.isSelected() ? null : NOTICE_REQUIRED;
    }

    private String validateRegister() {
        String problem = registerFormProblem();
        if (problem != null) {
            return problem;
        }
        return validCode(registerCode.getText()) ? null
                : "WhatsApp'a gelen 6 haneli kodu yazın. Kod gelmediyse \"Kod Gönder\"e basın.";
    }

    /** Kayıt kodu: numara kayıtlı olsa da olmasa da aynı cevap gelir (Crypto_Design §7.2). */
    private void sendRegistrationCode() {
        String problem = registerFormProblem();
        if (problem != null) {
            showStatus(registerStatus, problem, true);
            return;
        }
        busyFlag(sendCode).set(true);
        showStatus(registerStatus, "Kod gönderiliyor…", false);
        String phone = registerPhone.getText();
        Async.run(() -> auth.startRegistration(phone),
                testCode -> {
                    busyFlag(sendCode).set(false);
                    Cooldown.start(sendCode, Cooldown.CODE_SECONDS, true);
                    if (testCode.isPresent()) {
                        // Sunucu test modunda: WhatsApp'a mesaj gitmedi, kod cevapta geldi.
                        registerCode.setText(testCode.get());
                        showStatus(registerStatus, "TEST MODU: WhatsApp mesajı gönderilmedi, kod otomatik yazıldı.",
                                false);
                        registerSubmit.requestFocus();
                        return;
                    }
                    showStatus(registerStatus, "Kod WhatsApp'a gönderildi; 5 dakika geçerlidir. Numara zaten kayıtlıysa "
                            + "kod gelmez, giriş yapın ya da şifrenizi sıfırlayın.", false);
                    registerCode.requestFocus();
                },
                failure -> {
                    busyFlag(sendCode).set(false);
                    showStatus(registerStatus, Toasts.describe(failure), true);
                });
    }

    private static boolean validCode(String code) {
        return code != null && code.trim().matches("\\d{6}");
    }

    // ---------- Parola sıfırlama ----------

    /**
     * Parolasını unutan kullanıcı (Crypto_Design §7.5): WhatsApp koduyla yeni parola belirlenir, yeni anahtar çifti
     * üretilir. Eski özel anahtarlar açılamadığı için kullanıcıya gelmiş belgeler silinir; bu önceden açıkça söylenir.
     */
    private void openPasswordReset() {
        TextField phone = Inputs.phone(new TextField());
        phone.setText(loginPhone.getText());
        phone.setPromptText("Cep Telefonu (5xx) xxx xx xx");
        TextField code = Inputs.digits(new TextField(), 6);
        code.setPromptText("WhatsApp Kodu (6 hane)");
        PasswordTextField password = PasswordFields.limit(new PasswordTextField());
        password.setPromptText("Yeni Şifre (12–20 karakter)");
        PasswordTextField repeat = PasswordFields.limit(new PasswordTextField());
        repeat.setPromptText("Yeni Şifre (tekrar)");
        Label passwordHint = PasswordFields.hint();
        Label repeatHint = PasswordFields.hint();
        PasswordFields.bindHints(password, repeat, passwordHint, repeatHint,
                value -> PasswordStrength.check(value, phone.getText()));
        TextField totp = new TextField();
        totp.setPromptText("İki Adımlı Doğrulama Kodu (açtıysanız)");
        Label warning = Ui.muted("Şifre sıfırlanınca size gelmiş bütün belgeler silinir; eski şifrenizle "
                + "kilitliydiler ve yeni şifreyle açılamazlar. Kişilerinize anahtarınızın değiştiği bildirilir.");
        warning.setWrapText(true);
        warning.setMaxWidth(420);
        Label status = statusLabel();
        status.setMaxWidth(420);

        Button codeButton = Ui.button("Kod Gönder", Material2OutlinedMZ.SMS);
        Button cancel = Ui.button("Vazgeç", null);
        Button submit = Ui.primary("Şifreyi Sıfırla", Material2OutlinedAL.LOCK);
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> Dialogs.close());

        codeButton.setOnAction(e -> {
            String error = AuthService.validatePhone(phone.getText());
            if (error != null) {
                showStatus(status, error, true);
                return;
            }
            busyFlag(codeButton).set(true);
            Async.run(() -> {
                        auth.startPasswordReset(phone.getText());
                        return null;
                    },
                    ignored -> {
                        busyFlag(codeButton).set(false);
                        Cooldown.start(codeButton, Cooldown.CODE_SECONDS);
                        showStatus(status, "Kod WhatsApp'a gönderildi; 5 dakika geçerlidir. Numara kayıtlı değilse kod "
                                + "gelmez.", false);
                        code.requestFocus();
                    },
                    failure -> {
                        busyFlag(codeButton).set(false);
                        showStatus(status, Toasts.describe(failure), true);
                    });
        });
        // Telefon tam yazılmadan kod istenmez; form eksiksiz ve geçerli olmadan sıfırlanmaz (docs/UI_Standards.md).
        BooleanBinding phoneReady = Bindings.createBooleanBinding(() -> Phones.normalize(phone.getText()) != null,
                phone.textProperty());
        codeButton.disableProperty().bind(busyFlag(codeButton).or(Cooldown.running(codeButton)).or(phoneReady.not()));
        BooleanBinding resetReady = Bindings.createBooleanBinding(
                () -> Phones.normalize(phone.getText()) != null && validCode(code.getText())
                        && AuthService.validateNewPassword(password.getPassword().toCharArray(),
                        repeat.getPassword().toCharArray(), phone.getText()) == null,
                phone.textProperty(), code.textProperty(), password.passwordProperty(), repeat.passwordProperty());
        submit.disableProperty().bind(busyFlag(submit).or(resetReady.not()));

        submit.setOnAction(e -> {
            String error = AuthService.validatePhone(phone.getText());
            if (error == null) {
                error = AuthService.validateNewPassword(password.getPassword().toCharArray(),
                        repeat.getPassword().toCharArray(), phone.getText());
            }
            if (error == null && !validCode(code.getText())) {
                error = "WhatsApp'a gelen 6 haneli kodu yazın.";
            }
            if (error != null) {
                showStatus(status, error, true);
                return;
            }
            busyFlag(submit).set(true);
            showStatus(status, "Yeni anahtarlarınız üretiliyor ve şifreniz değiştiriliyor…", false);
            Async.run(() -> auth.completePasswordReset(phone.getText(), code.getText(),
                            password.getPassword().toCharArray(), totp.getText()),
                    session -> {
                        Dialogs.close();
                        Toasts.success("Şifreniz değiştirildi.");
                        onLoggedIn.accept(session);
                    },
                    failure -> {
                        if (isTotpRequired(failure) && ((ApiException) failure).status() == 401) {
                            // Parola değişti; sıfırlamada kullanılan kod tekrar geçmediği için giriş yeni kodla yapılır.
                            Dialogs.close();
                            loginPhone.setText(phone.getText());
                            Toasts.success("Şifreniz değiştirildi. Yeni şifreniz ve Authenticator kodunuzla giriş yapın.");
                            return;
                        }
                        busyFlag(submit).set(false);
                        String message = isTotpRequired(failure)
                                ? "Bu hesapta iki adımlı doğrulama açık: Authenticator ya da kurtarma kodunu yazın. "
                                        + "WhatsApp kodu kullanıldı; yeni kod isteyin."
                                : Toasts.describe(failure);
                        showStatus(status, message, true);
                    });
        });

        HBox phoneRow = new HBox(10, phone, codeButton);
        HBox.setHgrow(phone, javafx.scene.layout.Priority.ALWAYS);
        VBox body = new VBox(12, warning, phoneRow, code, new VBox(password, passwordHint),
                new VBox(repeat, repeatHint), totp, status);
        Dialogs.show("Şifremi Unuttum", body, cancel, submit);
    }

    private static void busy(Button submit, Label status, String message) {
        busyFlag(submit).set(true);
        showStatus(status, message, false);
    }

    private static void failed(Button submit, Label status, Throwable failure) {
        busyFlag(submit).set(false);
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
