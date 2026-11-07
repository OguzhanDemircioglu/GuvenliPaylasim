package com.desktop.ui.views;

import com.desktop.App;
import com.desktop.api.Dto;
import com.desktop.config.AppConfig;
import com.desktop.service.AuthService;
import com.desktop.service.Session;
import com.desktop.ui.Async;
import com.desktop.ui.Formats;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Ana ekran: solda kenar çubuğu, sağda seçili sayfa. Gelen kutusu arka planda düzenli kontrol edilir; yeni belge
 * gelince uygulama içinde bildirim gösterilir (v2'de kişilere SMS/WhatsApp bildirimi gitmez, Crypto_Design §7.7).
 */
public final class ShellView extends BorderPane {

    private final Context context;
    private final StackPane page = new StackPane();
    private final ToggleGroup nav = new ToggleGroup();
    private final Label inboxBadge = Ui.label("", "nav-badge");
    private final Label userPhone = Ui.label("", "user-unit");
    private final Label userName = Ui.label("", "user-name");
    private final StackPane userAvatar = new StackPane();
    private final Set<Long> knownDocuments = new HashSet<>();
    private final Timeline poller;
    private boolean firstPoll = true;

    public ShellView(App app, AuthService auth, Session session) {
        this.context = new Context(session, auth);
        context.onInboxChanged = this::pollInbox;
        context.onProfileChanged = () -> showName(session.fullName());

        page.getStyleClass().add("content-area");
        setLeft(sidebar(app, session.user()));
        setCenter(page);

        poller = new Timeline(new KeyFrame(Duration.seconds(AppConfig.get().inboxPollInterval().toSeconds()),
                e -> pollInbox()));
        poller.setCycleCount(Timeline.INDEFINITE);
        context.onAccountDeleted = () -> {
            poller.stop();
            app.logout();
        };
        sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) {
                poller.stop();
            }
        });

        userPhone.setText(Formats.phone(session.user().phone()));
        ((ToggleButton) nav.getToggles().getFirst()).fire();
        pollInbox();
        poller.play();
    }

    private VBox sidebar(App app, Dto.SessionUser user) {
        // Uygulamanın kendi logosu (pencere ve kurulum simgesiyle aynı kalkan).
        ImageView mark = new ImageView(new Image(ShellView.class.getResourceAsStream("/com/desktop/icon-128.png"),
                40, 40, true, true));
        HBox brand = Ui.row(11, mark, Ui.column(0, Ui.label("Güvenli Paylaşım", "brand-title"),
                Ui.label("Uçtan Uca Şifreli", "brand-subtitle")));
        brand.setPadding(new Insets(0, 4, 8, 4));

        inboxBadge.setVisible(false);
        VBox items = new VBox(3,
                brand,
                Ui.label("BELGELER", "nav-section"),
                navButton("Gelen Kutusu", Material2OutlinedAL.INBOX, () -> new InboxView(context), inboxBadge),
                navButton("Yeni Gönderi", Material2OutlinedMZ.SEND, () -> new ComposeView(context)),
                navButton("Gönderilenler", Material2OutlinedAL.HISTORY, () -> new SentView(context)),
                navButton("Kişiler", Material2OutlinedAL.CONTACTS, () -> new ContactsView(context)),
                Ui.label("GÜVENLİK", "nav-section"),
                navButton("Profilim", Material2OutlinedAL.FINGERPRINT, () -> new SecurityView(context)),
                navButton("Neden Güvenli?", Material2OutlinedMZ.VERIFIED_USER, WhyView::new));

        HBox securePill = Ui.row(6, Ui.icon(Material2OutlinedAL.LOCK, 13), Ui.label("Anahtarlarınız bu bilgisayarda"));
        securePill.getStyleClass().add("secure-pill");

        Button theme = iconButton(App.isDark() ? Material2OutlinedAL.BRIGHTNESS_7 : Material2OutlinedAL.BRIGHTNESS_4,
                "Açık / koyu tema");
        theme.setOnAction(e -> {
            App.toggleTheme();
            theme.setGraphic(Ui.icon(App.isDark() ? Material2OutlinedAL.BRIGHTNESS_7 : Material2OutlinedAL.BRIGHTNESS_4, 18));
        });
        Button logout = iconButton(Material2OutlinedAL.EXIT_TO_APP, "Güvenli Çıkış");
        logout.setOnAction(e -> {
            poller.stop();
            app.logout();
        });

        showName(user.fullName());
        userName.setWrapText(true);
        userPhone.setWrapText(true);
        VBox identity = Ui.column(1, userName, userPhone);
        HBox.setHgrow(identity, Priority.ALWAYS);
        VBox userCard = Ui.column(8, Ui.row(10, userAvatar, identity),
                Ui.row(4, Ui.grow(), theme, logout));
        userCard.getStyleClass().add("user-card");

        VBox sidebar = new VBox(items, Ui.grow(), securePill, new VBox(), userCard);
        VBox.setMargin(securePill, new Insets(0, 0, 10, 4));
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    private void showName(String fullName) {
        userName.setText(fullName);
        userAvatar.getChildren().setAll(Ui.avatar(fullName, 18));
    }

    private ToggleButton navButton(String text, Ikon ikon, Supplier<Node> view) {
        return navButton(text, ikon, view, null);
    }

    /** {@code trailing}: sağda gösterilecek öğe (ör. okunmamış sayısı). */
    private ToggleButton navButton(String text, Ikon ikon, Supplier<Node> view, Node trailing) {
        ToggleButton button = new ToggleButton(text, Ui.icon(ikon, 19));
        if (trailing != null) {
            HBox graphic = Ui.row(12, Ui.icon(ikon, 19), Ui.label(text), Ui.grow(), trailing);
            graphic.setPrefWidth(228);
            button.setText(null);
            button.setGraphic(graphic);
        }
        button.getStyleClass().add("nav-button");
        button.setToggleGroup(nav);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> {
            if (!button.isSelected()) {
                button.setSelected(true);
            }
            open(view.get());
        });
        return button;
    }

    private static Button iconButton(Ikon ikon, String tooltip) {
        Button button = new Button(null, Ui.icon(ikon, 18));
        button.getStyleClass().add("sidebar-icon-button");
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private void open(Node view) {
        view.setOpacity(0);
        page.getChildren().setAll(view);
        FadeTransition fade = new FadeTransition(Duration.millis(180), view);
        fade.setToValue(1);
        fade.play();
    }

    /**
     * Okunmamış sayısını günceller; ilk kontrolden sonra gelen her yeni belge için bildirim gösterir. Gönderilen
     * davetler kabul edildiyse alıcının kabul mührü de burada doğrulanır (Crypto_Design §7.6).
     */
    private void pollInbox() {
        Async.run(context.documents::inbox, this::onInbox, e -> {
            // Bağlantı geçici olarak koptuysa bir sonraki kontrolde yeniden denenir.
        });
        Async.run(context.invites::processVerifications, result -> {
            result.verified().forEach(name -> Toasts.success(name + " davet kodunuzu girdi ve kişilerinize eklendi."));
            result.failed().forEach(name -> Toasts.error(new com.desktop.crypto.CryptoException(name
                    + " için anahtar doğrulaması başarısız oldu; kişi eklenmedi. Sunucu araya sahte bir anahtar koymuş olabilir.")));
        }, e -> {
        });
    }

    private void onInbox(List<Dto.InboxItem> items) {
        long unread = items.stream().filter(i -> i.readAt() == null).count();
        inboxBadge.setText(String.valueOf(unread));
        inboxBadge.setVisible(unread > 0);

        for (Dto.InboxItem item : items) {
            if (knownDocuments.add(item.id()) && !firstPoll) {
                Toasts.info(item.senderName() + " size yeni bir "
                        + (item.kind() == Dto.DocumentKind.TEXT ? "şifreli yazı" : "şifreli belge") + " gönderdi.");
            }
        }
        firstPoll = false;
    }
}
