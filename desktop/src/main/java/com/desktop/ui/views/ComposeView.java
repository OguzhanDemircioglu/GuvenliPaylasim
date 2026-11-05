package com.desktop.ui.views;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import com.desktop.api.Dto;
import com.desktop.crypto.DocumentContent;
import com.desktop.service.DocumentService;
import com.desktop.ui.Async;
import com.desktop.ui.Formats;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
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
import javafx.util.Duration;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Yeni gönderi: düz yazı ya da dosya, alıcı birimler. İçerik bu bilgisayarda şifrelenir; seçilen birimlerdeki
 * her kişi için belge anahtarı ayrı ayrı sarmalanır.
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
    private final ObservableSet<Long> selectedUnits = FXCollections.observableSet();
    private final CheckBox allUnits = new CheckBox("Tüm birimlere gönder");
    private final Label summary = new Label();
    private final Button send = Ui.button("Şifrele ve gönder", Material2OutlinedAL.ENHANCED_ENCRYPTION,
            Styles.ACCENT, "send-button");
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
    private List<Dto.Unit> units = List.of();
    private List<Dto.Recipient> recipients = List.of();
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

        debounce.setOnFinished(e -> resolveRecipients());
        selectedUnits.addListener((SetChangeListener<Long>) c -> debounce.playFromStart());
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

    private VBox recipientsPanel() {
        CustomTextField search = new CustomTextField();
        search.setPromptText("Birim ara");
        search.setLeft(Ui.icon(Material2OutlinedMZ.SEARCH, 16));

        ListView<Dto.Unit> list = new ListView<>();
        list.getStyleClass().add("unit-list");
        list.setCellFactory(v -> new UnitCell());
        VBox.setVgrow(list, Priority.ALWAYS);

        Async.run(context.documents::units, loaded -> {
            units = loaded;
            FilteredList<Dto.Unit> filtered = new FilteredList<>(FXCollections.observableArrayList(loaded));
            search.textProperty().addListener((obs, old, query) -> filtered.setPredicate(unit ->
                    query.isBlank() || unit.name().toLowerCase(Locale.forLanguageTag("tr"))
                            .contains(query.toLowerCase(Locale.forLanguageTag("tr")))));
            list.setItems(filtered);
        });

        allUnits.selectedProperty().addListener((obs, old, all) -> {
            if (all) {
                units.forEach(u -> selectedUnits.add(u.id()));
            } else {
                selectedUnits.clear();
            }
            list.refresh();
        });

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

        return Ui.surface(Ui.label("Alıcı birimler", "section-title"),
                Ui.muted("Seçtiğiniz birimlerdeki herkes belgeyi kendi anahtarıyla açabilir."),
                search, allUnits, list, summaryBox, send);
    }

    private void resolveRecipients() {
        if (selectedUnits.isEmpty()) {
            recipients = List.of();
            updateSummary();
            return;
        }
        List<Long> ids = new ArrayList<>(selectedUnits);
        Async.run(() -> context.documents.recipients(ids), resolved -> {
            if (ids.size() == selectedUnits.size() && selectedUnits.containsAll(ids)) {
                recipients = resolved;
                updateSummary();
            }
        });
    }

    private void updateSummary() {
        if (selectedUnits.isEmpty()) {
            summary.setText("Henüz birim seçilmedi.");
        } else if (recipients.isEmpty()) {
            summary.setText(selectedUnits.size() + " birim seçildi, ancak bu birimlerde anahtarı olan kullanıcı yok.");
        } else {
            summary.setText(selectedUnits.size() + " birim · " + recipients.size()
                    + " kişi. Belge anahtarı her kişi için ayrı ayrı sarmalanacak.");
        }
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
            allUnits.setSelected(false);
            selectedUnits.clear();
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

    private final class UnitCell extends ListCell<Dto.Unit> {

        private final CheckBox box = new CheckBox();

        UnitCell() {
            box.setOnAction(e -> {
                Dto.Unit unit = getItem();
                if (unit == null) {
                    return;
                }
                if (box.isSelected()) {
                    selectedUnits.add(unit.id());
                } else {
                    selectedUnits.remove(unit.id());
                }
            });
        }

        @Override
        protected void updateItem(Dto.Unit unit, boolean empty) {
            super.updateItem(unit, empty);
            setText(null);
            if (empty || unit == null) {
                setGraphic(null);
                return;
            }
            box.setText(unit.name());
            box.setSelected(selectedUnits.contains(unit.id()));
            Node icon = Ui.icon(Material2OutlinedAL.BUSINESS, 16);
            icon.getStyleClass().add(Styles.TEXT_MUTED);
            setGraphic(Ui.row(10, box, Ui.grow(), icon));
        }
    }
}
