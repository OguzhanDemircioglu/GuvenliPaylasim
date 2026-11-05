package com.desktop.ui.views;

import atlantafx.base.theme.Styles;
import com.desktop.api.Dto;
import com.desktop.ui.Async;
import com.desktop.ui.Dialogs;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;

import java.util.List;

/** Birimler: belgeler birime göre seçilen kişilere gönderilir. Sadece yönetici değiştirebilir. */
final class UnitsView extends VBox {

    private final Context context;
    private final TableView<Dto.Unit> table = new TableView<>();

    UnitsView(Context context) {
        this.context = context;
        getStyleClass().add("page");

        TableColumn<Dto.Unit, String> name = new TableColumn<>("Birim");
        name.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
        TableColumn<Dto.Unit, String> description = new TableColumn<>("Açıklama");
        description.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().description()));
        TableColumn<Dto.Unit, Dto.Unit> actions = new TableColumn<>("");
        actions.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        actions.setCellFactory(c -> new ActionCell());
        actions.setPrefWidth(110);
        actions.setSortable(false);
        table.getColumns().addAll(List.of(name, description, actions));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().addAll(Styles.STRIPED);
        table.setPlaceholder(Ui.muted("Henüz birim yok."));
        VBox.setVgrow(table, Priority.ALWAYS);

        Button add = Ui.primary("Birim ekle", Material2OutlinedAL.ADD);
        add.setOnAction(e -> edit(null));
        HBox top = Ui.row(10, Ui.header("Birimler", "Bakanlık merkez ve taşra birimleri"), Ui.grow(), add);
        getChildren().addAll(top, Ui.surface(table));
        VBox.setVgrow(getChildren().getLast(), Priority.ALWAYS);
        load();
    }

    private void load() {
        Async.run(context.admin::units, units -> {
            context.units(units);
            table.getItems().setAll(units);
        });
    }

    private void edit(Dto.Unit unit) {
        TextField name = new TextField(unit == null ? "" : unit.name());
        name.setPromptText("Ör. Ankara Valiliği");
        TextField description = new TextField(unit == null || unit.description() == null ? "" : unit.description());
        description.setPromptText("İsteğe bağlı");
        VBox body = new VBox(8, Ui.label("Birim adı", "field-label"), name,
                Ui.label("Açıklama", "field-label"), description);

        Button cancel = Ui.button("Vazgeç", null);
        cancel.setOnAction(e -> Dialogs.close());
        Button save = Ui.primary("Kaydet", Material2OutlinedAL.DONE);
        save.setDefaultButton(true);
        save.setOnAction(e -> Async.run(() -> {
            context.admin.saveUnit(unit, name.getText().trim(), description.getText().trim());
            return true;
        }, ok -> {
            Dialogs.close();
            Toasts.success("Birim kaydedildi.");
            load();
        }));
        Dialogs.show(unit == null ? "Yeni birim" : "Birimi düzenle", body, cancel, save);
        name.requestFocus();
    }

    private void delete(Dto.Unit unit) {
        Dialogs.confirm("Birim silinsin mi?", unit.name() + " birimi silinecek.", "Sil",
                Material2OutlinedAL.DELETE, true, () -> Async.run(() -> {
                    context.admin.deleteUnit(unit);
                    return true;
                }, ok -> {
                    Toasts.success("Birim silindi.");
                    load();
                }));
    }

    private final class ActionCell extends TableCell<Dto.Unit, Dto.Unit> {

        @Override
        protected void updateItem(Dto.Unit unit, boolean empty) {
            super.updateItem(unit, empty);
            if (empty || unit == null) {
                setGraphic(null);
                return;
            }
            Button edit = Ui.iconButton(Material2OutlinedAL.EDIT, "Düzenle");
            edit.setOnAction(e -> edit(unit));
            Button delete = Ui.iconButton(Material2OutlinedAL.DELETE, "Sil", Styles.DANGER);
            delete.setOnAction(e -> delete(unit));
            setGraphic(Ui.row(4, edit, delete));
        }
    }
}
