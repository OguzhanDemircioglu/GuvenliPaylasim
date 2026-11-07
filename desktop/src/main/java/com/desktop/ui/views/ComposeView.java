package com.desktop.ui.views;

import atlantafx.base.theme.Styles;
import com.desktop.api.Dto;
import com.desktop.crypto.DocumentContent;
import com.desktop.service.AuthService;
import com.desktop.service.ContactService;
import com.desktop.service.DocumentService;
import com.desktop.service.InviteService;
import com.desktop.ui.Async;
import com.desktop.ui.Dialogs;
import com.desktop.ui.Formats;
import com.desktop.ui.Inputs;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Yeni gönderi: düz yazı ya da dosya; alıcı doğrulanmış kişiler ya da davet koduyla yeni bir numara. İçerik bu
 * bilgisayarda şifrelenir; her alıcı kişi için belge anahtarı ayrı ayrı sarmalanır.
 */
final class ComposeView extends VBox {

    /** Eski sistemle aynı: Word, PDF ve JPEG. */
    private static final Map<String, String> ALLOWED = Map.of(
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg");

    private final Context context;
    private final ToggleButton textMode = new ToggleButton("Düz Yazı", Ui.icon(Material2OutlinedMZ.TEXT_SNIPPET, 16));
    private final ToggleButton fileMode = new ToggleButton("Dosya", Ui.icon(Material2OutlinedAL.ATTACH_FILE, 16));
    private final TextArea text = new TextArea();
    private final StackPane fileArea = new StackPane();
    private final StackPane editor = new StackPane();
    private final Label summary = new Label();
    private final Button send = Ui.button("Şifrele ve Gönder", Material2OutlinedAL.ENHANCED_ENCRYPTION,
            Styles.ACCENT, "send-button");
    private final ToggleButton contactsMode = new ToggleButton("Kişilerim", Ui.icon(Material2OutlinedMZ.PEOPLE, 16));
    private final ToggleButton inviteMode = new ToggleButton("Davet Kodu ile", Ui.icon(Material2OutlinedMZ.VPN_KEY, 16));
    private final StackPane recipientEditor = new StackPane();
    private final ListView<ContactService.Contact> contactList = new ListView<>();
    private final ObservableSet<Long> selected = FXCollections.observableSet();
    private final TextField invitePhone = new TextField();
    private final ComboBox<Ttl> ttl = new ComboBox<>();
    /** Kutunun ömrü (Crypto_Design §8.2); Ttl ile aynı biçim. Varsayılan 7 gün. */
    private final ComboBox<Ttl> lifetime = new ComboBox<>();
    private final CheckBox deleteOnOpen = new CheckBox("Açılınca Sil");
    private File file;

    ComposeView(Context context) {
        this.context = context;
        getStyleClass().add("page");

        VBox content = contentPanel();
        HBox.setHgrow(content, Priority.ALWAYS);
        VBox recipientsPanel = recipientsPanel();
        recipientsPanel.setPrefWidth(480);
        recipientsPanel.setMinWidth(440);

        HBox body = new HBox(20, content, recipientsPanel);
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(Ui.header("Yeni Gönderi",
                "İçerik bu bilgisayarda şifrelenir; sunucu dahil alıcılar dışında kimse okuyamaz."), body);

        text.textProperty().addListener((obs, old, value) -> updateSendState());
        updateSendState();
    }

    // ---------- İçerik ----------

    private VBox contentPanel() {
        ToggleGroup mode = new ToggleGroup();
        textMode.setToggleGroup(mode);
        fileMode.setToggleGroup(mode);
        textMode.getStyleClass().add(Styles.LEFT_PILL);
        fileMode.getStyleClass().add(Styles.RIGHT_PILL);
        textMode.setSelected(true);
        mode.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                old.setSelected(true);
                return;
            }
            editor.getChildren().setAll(selected == textMode ? text : fileArea);
            updateSendState();
        });

        text.setPromptText("Şifrelenecek metni buraya yazın…");
        text.setWrapText(true);
        text.getStyleClass().add("document-text");
        showFilePicker();
        editor.getChildren().setAll(text);
        VBox.setVgrow(editor, Priority.ALWAYS);

        Button clear = Ui.button("Alanları Temizle", Material2OutlinedAL.CLOSE, Styles.FLAT);
        clear.setOnAction(e -> {
            text.clear();
            file = null;
            showFilePicker();
            updateSendState();
        });

        VBox panel = Ui.surface(
                Ui.row(10, Ui.label("İçerik", "section-title"), Ui.grow(), new HBox(textMode, fileMode)),
                editor,
                Ui.row(10, Ui.muted("En fazla 10 MB · Word, PDF ve JPEG"), Ui.grow(), clear));
        return panel;
    }

    private void showFilePicker() {
        Label title = Ui.label("Dosyayı buraya sürükleyin", Styles.TITLE_4);
        Button choose = Ui.button("Dosya Seç", Material2OutlinedAL.ATTACH_FILE, Styles.BUTTON_OUTLINED);
        choose.setOnAction(e -> chooseFile());
        VBox zone = new VBox(12, Ui.iconBadge(Material2OutlinedAL.CLOUD_UPLOAD, "accent", 60), title,
                Ui.muted("ya da bilgisayarınızdan seçin"), choose);
        zone.setAlignment(Pos.CENTER);
        zone.getStyleClass().add("drop-zone");
        zone.setOnMouseClicked(e -> chooseFile());
        zone.setOnDragOver(this::acceptDrag);
        zone.setOnDragEntered(e -> zone.getStyleClass().add("drag-over"));
        zone.setOnDragExited(e -> zone.getStyleClass().remove("drag-over"));
        zone.setOnDragDropped(e -> {
            List<File> files = e.getDragboard().getFiles();
            e.setDropCompleted(!files.isEmpty());
            if (!files.isEmpty()) {
                setFile(files.getFirst());
            }
        });
        fileArea.getChildren().setAll(zone);
    }

    private void acceptDrag(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Gönderilecek belgeyi seçin");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word, PDF ve resim dosyaları",
                ALLOWED.keySet().stream().map(ext -> "*." + ext).toList()));
        File chosen = chooser.showOpenDialog(getScene().getWindow());
        if (chosen != null) {
            setFile(chosen);
        }
    }

    private void setFile(File chosen) {
        if (mimeType(chosen) == null) {
            Toasts.info("Sadece Word, PDF ve JPEG dosyaları gönderilebilir.");
            return;
        }
        if (chosen.length() > DocumentService.MAX_FILE_BYTES) {
            Toasts.info("Dosya 10 MB'tan büyük olamaz (" + Formats.size(chosen.length()) + ").");
            return;
        }
        file = chosen;
        Button remove = Ui.iconButton(Material2OutlinedAL.CLOSE, "Dosyayı Kaldır");
        remove.setOnAction(e -> {
            file = null;
            showFilePicker();
            updateSendState();
        });
        HBox card = Ui.row(14, Ui.iconBadge(Material2OutlinedAL.DESCRIPTION, "accent", 48),
                Ui.column(3, Ui.label(chosen.getName(), Styles.TEXT_BOLD), Ui.muted(Formats.size(chosen.length())
                        + " · gönderirken bu bilgisayarda şifrelenecek")), Ui.grow(), remove);
        card.getStyleClass().add("file-card");
        VBox wrapper = new VBox(card);
        fileArea.getChildren().setAll(wrapper);
        updateSendState();
    }

    private static String mimeType(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? null : ALLOWED.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    // ---------- Alıcılar ----------

    /** Davet kodunun geçerlilik süresi (Crypto_Design §7.6, en fazla 7 gün). */
    private record Ttl(String label, int hours) {
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * İki yol (Crypto_Design §7.6–7.7): doğrulanmış kişilere kodsuz gönderim ya da yeni birine davet koduyla ilk kutu.
     * Anahtarı değişen kişi listede seçilemez; ona da davet koduyla gönderilir.
     */
    private VBox recipientsPanel() {
        ToggleGroup modes = new ToggleGroup();
        contactsMode.setToggleGroup(modes);
        inviteMode.setToggleGroup(modes);
        contactsMode.getStyleClass().add(Styles.LEFT_PILL);
        inviteMode.getStyleClass().add(Styles.RIGHT_PILL);
        contactsMode.setSelected(true);
        modes.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                old.setSelected(true);
                return;
            }
            recipientEditor.getChildren().setAll(selected == contactsMode ? contactList : inviteForm());
            updateSummary();
        });

        contactList.getStyleClass().add("unit-list");
        contactList.setCellFactory(v -> new ContactCell());
        contactList.setPlaceholder(Ui.muted("Henüz kişiniz yok. \"Davet kodu ile\" seçip ilk kutuyu gönderin."));
        Async.run(context.contacts::list, loaded -> {
            contactList.getItems().setAll(loaded);
            updateSummary();
        });
        recipientEditor.getChildren().setAll(contactList);
        VBox.setVgrow(recipientEditor, Priority.ALWAYS);

        ttl.getItems().setAll(new Ttl("1 saat", 1), new Ttl("1 gün", 24), new Ttl("3 gün", 72), new Ttl("7 gün", 168));
        ttl.getSelectionModel().select(1);
        ttl.setMaxWidth(Double.MAX_VALUE);
        Inputs.phone(invitePhone);
        invitePhone.setPromptText("Alıcının Cep Telefonu (5xx) xxx xx xx");
        invitePhone.textProperty().addListener((obs, old, value) -> updateSummary());
        ttl.valueProperty().addListener((obs, old, value) -> updateSummary());

        lifetime.getItems().setAll(new Ttl("1 saat", 1), new Ttl("1 gün", 24), new Ttl("7 gün", 168),
                new Ttl("30 gün", 720));
        lifetime.getSelectionModel().select(2);
        lifetime.setMaxWidth(Double.MAX_VALUE);
        lifetime.valueProperty().addListener((obs, old, value) -> updateSummary());
        deleteOnOpen.setTooltip(new javafx.scene.control.Tooltip("Alıcı açtığı anda kutu sunucudan silinir. Alıcı "
                + "içeriği kaydedebilir; bu seçenek sadece sunucuda kalmamasını sağlar."));
        HBox.setHgrow(lifetime, Priority.ALWAYS);
        HBox lifetimeRow = Ui.row(10, Ui.label("Kutunun Ömrü", Styles.TEXT_BOLD), lifetime, deleteOnOpen);

        summary.setWrapText(true);
        summary.setMinHeight(Region.USE_PREF_SIZE);
        summary.setMaxWidth(Double.MAX_VALUE);
        VBox summaryBox = new VBox(summary);
        summaryBox.getStyleClass().add("recipient-summary");
        summary.setGraphic(Ui.icon(Material2OutlinedMZ.PEOPLE, 18));
        summary.setGraphicTextGap(10);
        updateSummary();

        send.setMaxWidth(Double.MAX_VALUE);
        send.setOnAction(e -> send());

        return Ui.surface(Ui.row(10, Ui.label("Alıcı", "section-title"), Ui.grow(), new HBox(contactsMode, inviteMode)),
                recipientEditor, lifetimeRow, summaryBox, send);
    }

    private VBox inviteForm() {
        Label help = Ui.muted("İlk kutu bir kodla kilitlenir. Kodu alıcıya siz iletirsiniz (yüz yüze, telefonla). "
                + "Alıcı kayıtlı değilse bu sürede kayıt olabilir. Kodu girene kadar kutuyu göremez.");
        help.setWrapText(true);
        VBox form = new VBox(10, help, Ui.label("Cep Telefonu", Styles.TEXT_BOLD), invitePhone,
                Ui.label("Kod Geçerlilik Süresi", Styles.TEXT_BOLD), ttl);
        return form;
    }

    private List<Dto.Recipient> selectedRecipients() {
        return contactList.getItems().stream()
                .filter(c -> c.canReceive() && selected.contains(c.userId()))
                .map(ContactService.Contact::recipient)
                .toList();
    }

    private void updateSummary() {
        if (inviteMode.isSelected()) {
            String error = AuthService.validatePhone(invitePhone.getText());
            summary.setText(error != null ? "Alıcının telefonunu yazın."
                    : codeOutlivesBox() ? "Kodun geçerlilik süresi kutunun ömründen uzun olamaz."
                    : "Kod ekranda gösterilecek; sunucuya gönderilmez. Kutu kod girilene kadar kimseye görünmez.");
        } else {
            int count = selectedRecipients().size();
            summary.setText(count == 0
                    ? "Doğrulanmış kişilerinizden seçin."
                    : count + " kişi. Belge anahtarı her kişi için ayrı ayrı sarmalanacak.");
        }
        updateSendState();
    }

    private void updateSendState() {
        boolean hasContent = textMode.isSelected() ? !text.getText().isBlank() : file != null;
        boolean hasRecipient = inviteMode.isSelected()
                ? AuthService.validatePhone(invitePhone.getText()) == null && !codeOutlivesBox()
                : !selectedRecipients().isEmpty();
        send.setDisable(!hasContent || !hasRecipient);
    }

    private boolean codeOutlivesBox() {
        return ttl.getValue() != null && lifetime.getValue() != null && ttl.getValue().hours() > lifetime.getValue().hours();
    }

    // ---------- Gönderim ----------

    private void send() {
        boolean isText = textMode.isSelected();
        String plain = text.getText();
        File chosen = file;
        if (inviteMode.isSelected()) {
            sendInvite(isText, plain, chosen);
            return;
        }
        List<Dto.Recipient> to = selectedRecipients();
        long size = isText ? plain.getBytes(StandardCharsets.UTF_8).length : chosen.length();

        SendProgress progress = new SendProgress(to, size, this::clearAll);
        progress.show();
        int hours = lifetime.getValue().hours();
        boolean once = deleteOnOpen.isSelected();
        Async.run(() -> context.documents.send(content(isText, plain, chosen), to, hours, once),
                progress::completed,
                progress::failed);
    }

    private void sendInvite(boolean isText, String plain, File chosen) {
        String phone = invitePhone.getText();
        int codeHours = ttl.getValue().hours();
        int boxHours = lifetime.getValue().hours();
        boolean once = deleteOnOpen.isSelected();
        send.setDisable(true);
        Async.run(() -> context.invites.send(content(isText, plain, chosen), phone, codeHours, boxHours, once),
                sent -> {
                    clearAll();
                    invitePhone.clear();
                    showCode(sent);
                },
                failure -> {
                    updateSendState();
                    Toasts.error(failure);
                });
    }

    /** Kod sadece burada, bir kez gösterilir; hiçbir yere kaydedilmez. */
    private void showCode(InviteService.SentCode sent) {
        Label code = Ui.label(sent.code(), "invite-code");
        Button copy = Ui.button("Kopyala", Material2OutlinedAL.CONTENT_COPY, Styles.BUTTON_OUTLINED);
        copy.setOnAction(e -> {
            ClipboardContent clip = new ClipboardContent();
            clip.putString(sent.code());
            Clipboard.getSystemClipboard().setContent(clip);
            Toasts.info("Kod panoya kopyalandı.");
        });
        Label text = Ui.muted("Bu kodu " + Formats.phone(sent.recipientPhone()) + " numaralı kişiye kendi kanalınızla "
                + "(yüz yüze, telefonla, başka bir uygulamayla) iletin. Kod sunucuya gönderilmedi ve bir daha "
                + "gösterilemez. Son geçerlilik: " + Formats.fullDate(sent.expiresAt()) + ".");
        text.setWrapText(true);
        text.setMaxWidth(440);
        Button done = Ui.primary("Tamam", Material2OutlinedAL.DONE);
        done.setDefaultButton(true);
        done.setOnAction(e -> Dialogs.close());
        Dialogs.show("Davet Kodu", new VBox(14, Ui.row(12, code, copy), text), done);
    }

    private static DocumentContent content(boolean isText, String plain, File chosen) throws java.io.IOException {
        return isText ? DocumentContent.ofText(plain)
                : DocumentContent.ofFile(chosen.getName(), mimeType(chosen), Files.readAllBytes(chosen.toPath()));
    }

    private void clearAll() {
        text.clear();
        file = null;
        showFilePicker();
        selected.clear();
        contactList.refresh();
        updateSummary();
    }

    private final class ContactCell extends ListCell<ContactService.Contact> {

        private final CheckBox box = new CheckBox();

        ContactCell() {
            box.setOnAction(e -> {
                ContactService.Contact contact = getItem();
                if (contact == null) {
                    return;
                }
                if (box.isSelected()) {
                    selected.add(contact.userId());
                } else {
                    selected.remove(contact.userId());
                }
                updateSummary();
            });
        }

        @Override
        protected void updateItem(ContactService.Contact contact, boolean empty) {
            super.updateItem(contact, empty);
            setText(null);
            if (empty || contact == null) {
                setGraphic(null);
                return;
            }
            box.setText(contact.fullName());
            box.setDisable(!contact.canReceive());
            box.setSelected(contact.canReceive() && selected.contains(contact.userId()));
            Label state = switch (contact.state()) {
                case VERIFIED -> Ui.muted(Formats.phone(contact.phone()));
                case PENDING -> Ui.chip("doğrulama bekleniyor", "accent");
                case KEY_CHANGED -> Ui.chip("anahtarı değişti · davet kodu gerekir", "warning");
                case UNTRUSTED -> Ui.chip("kayıt doğrulanamadı", "danger");
            };
            setGraphic(Ui.row(10, box, Ui.grow(), state));
        }
    }
}
