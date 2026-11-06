package com.desktop.ui.views;

import atlantafx.base.theme.Styles;
import com.desktop.api.Dto;
import com.desktop.crypto.DocumentContent;
import com.desktop.service.DocumentService;
import com.desktop.ui.Async;
import com.desktop.ui.Formats;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
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
 * Yeni gönderi: düz yazı ya da dosya. İçerik bu bilgisayarda şifrelenir; her alıcı kişi için belge anahtarı ayrı
 * ayrı sarmalanır.
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
    private final ToggleButton textMode = new ToggleButton("Düz yazı", Ui.icon(Material2OutlinedMZ.TEXT_SNIPPET, 16));
    private final ToggleButton fileMode = new ToggleButton("Dosya", Ui.icon(Material2OutlinedAL.ATTACH_FILE, 16));
    private final TextArea text = new TextArea();
    private final StackPane fileArea = new StackPane();
    private final StackPane editor = new StackPane();
    private final Label summary = new Label();
    private final Button send = Ui.button("Şifrele ve gönder", Material2OutlinedAL.ENHANCED_ENCRYPTION,
            Styles.ACCENT, "send-button");
    /** 4. adımda kişi listesinden ya da davet kodundan dolar. */
    private final List<Dto.Recipient> recipients = List.of();
    private File file;

    ComposeView(Context context) {
        this.context = context;
        getStyleClass().add("page");

        VBox content = contentPanel();
        HBox.setHgrow(content, Priority.ALWAYS);
        VBox recipientsPanel = recipientsPanel();
        recipientsPanel.setPrefWidth(400);
        recipientsPanel.setMinWidth(360);

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

        Button clear = Ui.button("Alanları temizle", Material2OutlinedAL.CLOSE, Styles.FLAT);
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
        Button choose = Ui.button("Dosya seç", Material2OutlinedAL.ATTACH_FILE, Styles.BUTTON_OUTLINED);
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
        Button remove = Ui.iconButton(Material2OutlinedAL.CLOSE, "Dosyayı kaldır");
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

    /**
     * v2'de alıcı birim değil kişidir: kişi listesi ve ilk temas için davet kodu kodlama sırasının 4. adımında
     * gelir (Crypto_Design §7.6–7.7). O zamana kadar alıcı seçilemez ve gönderim kapalıdır.
     */
    private VBox recipientsPanel() {
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

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return Ui.surface(Ui.label("Alıcı", "section-title"),
                Ui.muted("Belge, telefon numarasıyla eklediğiniz kişilere gider. İlk gönderim davet koduyla yapılır."),
                spacer, summaryBox, send);
    }

    private void updateSummary() {
        summary.setText(recipients.isEmpty()
                ? "Kişi listesi ve davet kodu henüz hazır değil; bu sürümde alıcı seçilemez."
                : recipients.size() + " kişi. Belge anahtarı her kişi için ayrı ayrı sarmalanacak.");
        updateSendState();
    }

    private void updateSendState() {
        boolean hasContent = textMode.isSelected() ? !text.getText().isBlank() : file != null;
        send.setDisable(!hasContent || recipients.isEmpty());
    }

    // ---------- Gönderim ----------

    private void send() {
        List<Dto.Recipient> to = List.copyOf(recipients);
        boolean isText = textMode.isSelected();
        String plain = text.getText();
        File chosen = file;
        long size = isText ? plain.getBytes(StandardCharsets.UTF_8).length : chosen.length();

        SendProgress progress = new SendProgress(to, size, () -> {
            text.clear();
            file = null;
            showFilePicker();
        });
        progress.show();
        Async.run(() -> {
                    DocumentContent content = isText ? DocumentContent.ofText(plain)
                            : DocumentContent.ofFile(chosen.getName(), mimeType(chosen), Files.readAllBytes(chosen.toPath()));
                    return context.documents.send(content, to);
                },
                progress::completed,
                progress::failed);
    }
}
