package com.desktop.ui.views;

import atlantafx.base.theme.Styles;
import com.desktop.api.Dto;
import com.desktop.crypto.DocumentContent;
import com.desktop.crypto.DocumentCrypto;
import com.desktop.ui.Async;
import com.desktop.ui.Dialogs;
import com.desktop.ui.Formats;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Gelen belgeler. Belge açılınca imzası doğrulanır ve içerik sadece bu bilgisayarda çözülür. */
final class InboxView extends VBox {

    private final Context context;
    private final ListView<Dto.InboxItem> list = new ListView<>();
    private final StackPane detail = new StackPane();
    private final Label count = Ui.muted("");
    /** Ayrıntı panelinde gösterilen belge. */
    private Long shownId;
    private boolean refreshing;

    InboxView(Context context) {
        this.context = context;
        getStyleClass().add("page");

        list.getStyleClass().add("inbox-list");
        list.setCellFactory(v -> new InboxCell());
        list.setPlaceholder(Ui.emptyState(Material2OutlinedAL.INBOX, "Gelen kutunuz boş",
                "Size bir belge gönderildiğinde burada görünür ve bildirim alırsınız."));
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            // Liste yenilenirken seçim geçici olarak boşalır ve aynı belge yeniden seçilir; açılmış belge
            // tekrar kilitlenmesin diye bu sırada ayrıntı paneline dokunulmaz.
            if (!refreshing && (item == null || !item.id().equals(shownId))) {
                showDetail(item);
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        Button refresh = Ui.button("Yenile", Material2OutlinedMZ.REFRESH, Styles.FLAT);
        refresh.setOnAction(e -> load());
        VBox left = new VBox(10, Ui.row(8, count, Ui.grow(), refresh), list);
        left.setMinWidth(360);
        left.setPrefWidth(400);
        left.setMaxWidth(440);

        detail.getStyleClass().add("surface");
        HBox.setHgrow(detail, Priority.ALWAYS);
        showDetail(null);

        HBox body = new HBox(20, left, detail);
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(Ui.header("Gelen Kutusu", "Size gönderilen şifreli yazılar ve belgeler"), body);
        load();
    }

    private void load() {
        Async.run(context.documents::inbox, items -> {
            refreshing = true;
            list.getItems().setAll(items);
            items.stream().filter(i -> i.id().equals(shownId)).findFirst()
                    .ifPresent(i -> list.getSelectionModel().select(i));
            refreshing = false;
            if (shownId != null && items.stream().noneMatch(i -> i.id().equals(shownId))) {
                showDetail(null);
            }
            long unread = items.stream().filter(i -> i.readAt() == null).count();
            count.setText(items.size() + " belge · " + unread + " okunmamış");
        });
    }

    private void showDetail(Dto.InboxItem item) {
        shownId = item == null ? null : item.id();
        if (item == null) {
            detail.getChildren().setAll(Ui.emptyState(Material2OutlinedAL.LOCK, "Bir belge seçin",
                    "Belgeler sunucuda şifreli durur. Açtığınızda bu bilgisayarda, sizin özel anahtarınızla çözülür."));
            return;
        }
        Button open = Ui.primary("Belgeyi aç", Material2OutlinedAL.LOCK_OPEN);
        open.setOnAction(e -> openDocument(item, open));

        VBox locked = new VBox(12, Ui.iconBadge(Material2OutlinedAL.ENHANCED_ENCRYPTION, "accent", 56),
                Ui.label("Bu belge şifreli", Styles.TITLE_4),
                Ui.muted("Açtığınızda önce gönderenin imzası doğrulanır, sonra içerik bu bilgisayarda çözülür."),
                open);
        locked.setAlignment(Pos.CENTER);
        locked.getStyleClass().add("locked-panel");

        VBox content = new VBox(18, documentHeader(item), locked);
        content.setFillWidth(true);
        detail.getChildren().setAll(content);
    }

    private Node documentHeader(Dto.InboxItem item) {
        Label title = Ui.label(item.kind() == Dto.DocumentKind.TEXT ? "Şifreli yazı" : "Şifreli belge", Styles.TITLE_3);
        Label meta = Ui.muted(item.senderName() + " · " + Formats.fullDate(item.createdAt()));
        Label number = Ui.chip("No " + item.id(), "neutral");
        Label size = Ui.chip(Formats.size(item.sizeBytes()) + " şifreli", "neutral");
        return Ui.row(14, Ui.avatar(item.senderName(), 22), Ui.column(3, title, meta), Ui.grow(), number, size);
    }

    private void openDocument(Dto.InboxItem item, Button open) {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(18, 18);
        open.setGraphic(spinner);
        open.setText("İmza doğrulanıyor ve çözülüyor…");
        open.setDisable(true);
        Async.run(() -> context.documents.open(item.id()),
                opened -> {
                    showOpened(item, opened);
                    context.onInboxChanged.run();
                    load();
                },
                failure -> {
                    open.setDisable(false);
                    open.setText("Belgeyi aç");
                    open.setGraphic(Ui.icon(Material2OutlinedAL.LOCK_OPEN, 16));
                    Toasts.error(failure);
                });
    }

    private void showOpened(Dto.InboxItem item, DocumentCrypto.Opened opened) {
        DocumentContent content = opened.content();
        Node body = content.text() ? textBody(content) : fileBody(content);
        VBox.setVgrow(body, Priority.ALWAYS);

        Button delete = Ui.button("Okudum ve sil", Material2OutlinedAL.DELETE_FOREVER, Styles.DANGER, Styles.BUTTON_OUTLINED);
        delete.setOnAction(e -> Dialogs.confirm("Belge kalıcı olarak silinsin mi?",
                "Belge sizin kutunuzdan silinir. Bütün alıcılar sildiğinde şifreli içerik sunucudan da kalıcı "
                        + "olarak silinir. Bu işlem geri alınamaz.",
                "Okudum, sil", Material2OutlinedAL.DELETE_FOREVER, true,
                () -> Async.run(() -> {
                    context.documents.delete(item.id());
                    return true;
                }, ok -> {
                    Toasts.success("Belge silindi.");
                    list.getItems().remove(item);
                    context.onInboxChanged.run();
                    load();
                })));

        VBox page = new VBox(16, documentHeader(item), signatureBanner(item, opened.signature()), body,
                Ui.row(10, Ui.grow(), delete));
        detail.getChildren().setAll(page);
    }

    private static Node signatureBanner(Dto.InboxItem item, DocumentCrypto.SignatureStatus status) {
        boolean verified = status == DocumentCrypto.SignatureStatus.VERIFIED;
        Ikon ikon = verified ? Material2OutlinedMZ.VERIFIED_USER : Material2OutlinedMZ.WARNING;
        String text = verified
                ? "İmza doğrulandı: bu belge " + item.senderName() + " tarafından gönderildi ve yolda değiştirilmedi."
                : "Gönderenin anahtarları sonradan sıfırlanmış; imza doğrulanamıyor. İçeriğe dikkatle yaklaşın.";
        Label label = new Label(text, Ui.icon(ikon, 20));
        label.setWrapText(true);
        label.setGraphicTextGap(10);
        HBox banner = new HBox(label);
        banner.getStyleClass().add(verified ? "signature-ok" : "signature-unknown");
        return banner;
    }

    private static Node textBody(DocumentContent content) {
        TextArea text = new TextArea(content.asText());
        text.setEditable(false);
        text.setWrapText(true);
        text.getStyleClass().add("document-text");
        return text;
    }

    private Node fileBody(DocumentContent content) {
        Ikon ikon = content.mimeType().contains("pdf") ? Material2OutlinedMZ.PICTURE_AS_PDF
                : content.mimeType().startsWith("image") ? Material2OutlinedAL.IMAGE : Material2OutlinedAL.DESCRIPTION;
        Button save = Ui.primary("Bilgisayara kaydet", Material2OutlinedMZ.SAVE_ALT);
        save.setOnAction(e -> save(content));
        HBox file = Ui.row(14, Ui.iconBadge(ikon, "accent", 48),
                Ui.column(3, Ui.label(content.fileName(), Styles.TEXT_BOLD), Ui.muted(Formats.size(content.data().length)
                        + " · çözüldü, sadece bu bilgisayarda")), Ui.grow(), save);
        file.getStyleClass().add("file-card");
        VBox box = new VBox(file);
        box.setPadding(new Insets(4, 0, 0, 0));
        return box;
    }

    private void save(DocumentContent content) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Belgeyi kaydet");
        chooser.setInitialFileName(content.fileName());
        File target = chooser.showSaveDialog(getScene().getWindow());
        if (target == null) {
            return;
        }
        Async.run(() -> {
            Files.write(target.toPath(), content.data());
            return target;
        }, file -> Toasts.success("Kaydedildi: " + file.getName()), failure -> {
            if (failure instanceof IOException) {
                Toasts.info("Dosya kaydedilemedi: " + failure.getMessage());
            } else {
                Toasts.error(failure);
            }
        });
    }

    private final class InboxCell extends ListCell<Dto.InboxItem> {

        @Override
        protected void updateItem(Dto.InboxItem item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            Label sender = Ui.label(item.senderName(), "inbox-sender");
            Label when = Ui.muted(Formats.shortDate(item.createdAt()));
            Ikon kindIcon = item.kind() == Dto.DocumentKind.TEXT ? Material2OutlinedMZ.TEXT_SNIPPET : Material2OutlinedAL.ATTACH_FILE;
            Label kind = new Label(item.kind() == Dto.DocumentKind.TEXT ? "Şifreli yazı" : "Şifreli belge",
                    Ui.icon(kindIcon, 14));
            kind.getStyleClass().add(Styles.TEXT_MUTED);

            Circle dot = new Circle(4);
            dot.getStyleClass().add("unread-dot");
            dot.setVisible(item.readAt() == null);

            HBox top = Ui.row(8, sender, Ui.grow(), when);
            HBox bottom = Ui.row(8, kind, Ui.grow(), dot);
            VBox text = new VBox(4, top, bottom);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = Ui.row(12, Ui.avatar(item.senderName(), 19), text);
            row.getStyleClass().add("inbox-item");
            if (item.readAt() == null) {
                sender.getStyleClass().add(Styles.TEXT_BOLDER);
            }
            setGraphic(row);
        }
    }
}
