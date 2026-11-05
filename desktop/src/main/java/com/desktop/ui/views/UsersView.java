package com.desktop.ui.views;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import com.desktop.api.Dto;
import com.desktop.service.AuthService;
import com.desktop.ui.Async;
import com.desktop.ui.Dialogs;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.List;
import java.util.Map;

/**
 * Kullanıcı yönetimi. Yöneticinin verdiği ilk parola sunucuya türetilmiş haliyle gider; kullanıcı ilk girişte
 * değiştirir. Parola sıfırlanınca kullanıcının anahtarları silinir ve eski belgeleri açılamaz hale gelir.
 */
final class UsersView extends VBox {

    private static final Map<Dto.Role, String> ROLE_NAMES = Map.of(
            Dto.Role.SUPERADMIN, "Süper yönetici", Dto.Role.ADMIN, "Yönetici",
            Dto.Role.OWNER, "Proje sahibi", Dto.Role.USER, "Kullanıcı");

    private final Context context;
    private final TableView<Dto.User> table = new TableView<>();
    private List<Dto.Unit> units = List.of();

    UsersView(Context context) {
        this.context = context;
        getStyleClass().add("page");

        TableColumn<Dto.User, Dto.User> name = new TableColumn<>("Ad soyad");
        name.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        name.setCellFactory(c -> new NameCell());
        name.setPrefWidth(260);
        TableColumn<Dto.User, String> unit = new TableColumn<>("Birim");
        unit.setCellValueFactory(c -> new ReadOnlyStringWrapper(context.unitName(c.getValue().unitId())));
        TableColumn<Dto.User, String> role = new TableColumn<>("Rol");
        role.setCellValueFactory(c -> new ReadOnlyStringWrapper(ROLE_NAMES.get(c.getValue().role())));
        TableColumn<Dto.User, Dto.User> status = new TableColumn<>("Durum");
        status.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        status.setCellFactory(c -> new StatusCell());
        TableColumn<Dto.User, Dto.User> actions = new TableColumn<>("");
        actions.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        actions.setCellFactory(c -> new ActionCell());
        actions.setSortable(false);
        actions.setPrefWidth(130);
        table.getColumns().addAll(List.of(name, unit, role, status, actions));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(54);
        table.setPlaceholder(Ui.muted("Henüz kullanıcı yok."));
        VBox.setVgrow(table, Priority.ALWAYS);

        Button add = Ui.primary("Kullanıcı ekle", Material2OutlinedAL.ADD);
        add.setOnAction(e -> create());
        HBox top = Ui.row(10, Ui.header("Kullanıcılar", "Belge gönderip alabilecek kişiler"), Ui.grow(), add);
        VBox card = Ui.surface(table);
        VBox.setVgrow(card, Priority.ALWAYS);
        getChildren().addAll(top, card);
        load();
    }

    private void load() {
        Async.run(() -> {
            units = context.admin.units();
            return context.admin.users();
        }, users -> {
            context.units(units);
            table.getItems().setAll(users);
        });
    }

    private void create() {
        TextField email = new TextField();
        email.setPromptText("ad.soyad@kurum.gov.tr");
        TextField fullName = new TextField();
        ComboBox<Dto.Unit> unit = unitBox(null);
        ComboBox<Dto.Role> role = roleBox(Dto.Role.USER);
        TextField phone = new TextField();
        phone.setPromptText("05xx xxx xx xx (SMS bildirimi için)");
        PasswordTextField password = new PasswordTextField();
        password.setPromptText("En az 10 karakter, harf ve rakam");

        GridPane form = form();
        form.addRow(0, Ui.label("E-posta", "field-label"), email);
        form.addRow(1, Ui.label("Ad soyad", "field-label"), fullName);
        form.addRow(2, Ui.label("Birim", "field-label"), unit);
        form.addRow(3, Ui.label("Rol", "field-label"), role);
        form.addRow(4, Ui.label("Telefon", "field-label"), phone);
        form.addRow(5, Ui.label("İlk parola", "field-label"), password);
        Label note = Ui.muted("Kullanıcı ilk girişte parolasını değiştirir; şifreleme anahtarları o anda kendi "
                + "bilgisayarında üretilir.");
        note.setMaxWidth(440);

        Button cancel = Ui.button("Vazgeç", null);
        cancel.setOnAction(e -> Dialogs.close());
        Button save = Ui.primary("Kullanıcıyı oluştur", Material2OutlinedAL.DONE);
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            String invalid = AuthService.validateNewPassword(password.getPassword().toCharArray(),
                    password.getPassword().toCharArray());
            if (invalid != null) {
                Toasts.info(invalid.replace("Yeni parola", "İlk parola"));
                return;
            }
            Async.run(() -> {
                context.admin.createUser(email.getText(), fullName.getText().trim(), password.getPassword().toCharArray(),
                        role.getValue(), unit.getValue() == null ? null : unit.getValue().id(), phone.getText());
                return true;
            }, ok -> {
                Dialogs.close();
                Toasts.success(fullName.getText().trim() + " oluşturuldu.");
                load();
            });
        });
        Dialogs.show("Yeni kullanıcı", new VBox(14, form, note), cancel, save);
    }

    private void edit(Dto.User user) {
        TextField fullName = new TextField(user.fullName());
        ComboBox<Dto.Unit> unit = unitBox(user.unitId());
        ComboBox<Dto.Role> role = roleBox(user.role());
        TextField phone = new TextField(user.phone() == null ? "" : user.phone());
        CheckBox active = new CheckBox("Aktif");
        active.setSelected(user.active());

        GridPane form = form();
        form.addRow(0, Ui.label("E-posta", "field-label"), Ui.muted(user.email()));
        form.addRow(1, Ui.label("Ad soyad", "field-label"), fullName);
        form.addRow(2, Ui.label("Birim", "field-label"), unit);
        form.addRow(3, Ui.label("Rol", "field-label"), role);
        form.addRow(4, Ui.label("Telefon", "field-label"), phone);
        form.addRow(5, new Label(), active);

        Button cancel = Ui.button("Vazgeç", null);
        cancel.setOnAction(e -> Dialogs.close());
        Button save = Ui.primary("Kaydet", Material2OutlinedAL.DONE);
        save.setDefaultButton(true);
        save.setOnAction(e -> Async.run(() -> {
            context.admin.updateUser(user, fullName.getText().trim(), role.getValue(), active.isSelected(),
                    unit.getValue() == null ? null : unit.getValue().id(), phone.getText());
            return true;
        }, ok -> {
            Dialogs.close();
            Toasts.success("Kullanıcı güncellendi.");
            load();
        }));
        Dialogs.show("Kullanıcıyı düzenle", form, cancel, save);
    }

    private void resetPassword(Dto.User user) {
        PasswordTextField password = new PasswordTextField();
        password.setPromptText("Yeni geçici parola");
        Label warning = new Label("Kullanıcının anahtarları silinecek. Kendisine daha önce gönderilmiş belgeler "
                + "artık açılamaz. Bu, uçtan uca şifrelemenin doğal sonucudur: yönetici dahil kimse anahtarları "
                + "kurtaramaz.", Ui.icon(Material2OutlinedMZ.WARNING, 18));
        warning.setWrapText(true);
        warning.setGraphicTextGap(10);
        warning.setMaxWidth(440);
        HBox warningBox = new HBox(warning);
        warningBox.getStyleClass().add("signature-unknown");

        Button cancel = Ui.button("Vazgeç", null);
        cancel.setOnAction(e -> Dialogs.close());
        Button reset = Ui.button("Parolayı sıfırla", Material2OutlinedMZ.VPN_KEY, Styles.DANGER);
        reset.setOnAction(e -> {
            String invalid = AuthService.validateNewPassword(password.getPassword().toCharArray(),
                    password.getPassword().toCharArray());
            if (invalid != null) {
                Toasts.info(invalid);
                return;
            }
            Async.run(() -> {
                context.admin.resetPassword(user, password.getPassword().toCharArray());
                return true;
            }, ok -> {
                Dialogs.close();
                Toasts.success(user.fullName() + " ilk girişte yeni parola belirleyecek.");
                load();
            });
        });
        Dialogs.show(user.fullName() + " · parola sıfırlama", new VBox(14, warningBox,
                Ui.label("Geçici parola", "field-label"), password), cancel, reset);
    }

    private ComboBox<Dto.Unit> unitBox(Long selectedId) {
        ComboBox<Dto.Unit> box = new ComboBox<>();
        box.getItems().setAll(units);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setPromptText("Birim seçin");
        units.stream().filter(u -> u.id().equals(selectedId)).findFirst().ifPresent(box::setValue);
        return box;
    }

    private static ComboBox<Dto.Role> roleBox(Dto.Role selected) {
        ComboBox<Dto.Role> box = new ComboBox<>();
        box.getItems().setAll(Dto.Role.USER, Dto.Role.ADMIN, Dto.Role.OWNER);
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(Dto.Role role) {
                return role == null ? "" : ROLE_NAMES.get(role);
            }

            @Override
            public Dto.Role fromString(String text) {
                return null;
            }
        });
        box.setValue(selected);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static GridPane form() {
        GridPane form = new GridPane(14, 10);
        form.setPrefWidth(460);
        ColumnConstraints labels = new ColumnConstraints(90);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(labels, fields);
        return form;
    }

    private final class NameCell extends TableCell<Dto.User, Dto.User> {
        @Override
        protected void updateItem(Dto.User user, boolean empty) {
            super.updateItem(user, empty);
            setGraphic(empty || user == null ? null : Ui.row(10, Ui.avatar(user.fullName(), 16),
                    Ui.column(0, Ui.label(user.fullName(), Styles.TEXT_BOLD), Ui.muted(user.email()))));
        }
    }

    private static final class StatusCell extends TableCell<Dto.User, Dto.User> {
        @Override
        protected void updateItem(Dto.User user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null) {
                setGraphic(null);
            } else if (!user.active()) {
                setGraphic(Ui.chip("Pasif", "neutral"));
            } else if (user.mustChangePassword()) {
                setGraphic(Ui.chip("İlk giriş bekleniyor", "warning"));
            } else {
                setGraphic(Ui.chip("Aktif", "success"));
            }
        }
    }

    private final class ActionCell extends TableCell<Dto.User, Dto.User> {
        @Override
        protected void updateItem(Dto.User user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null || user.role() == Dto.Role.SUPERADMIN) {
                setGraphic(null);
                return;
            }
            Button edit = Ui.iconButton(Material2OutlinedAL.EDIT, "Düzenle");
            edit.setOnAction(e -> edit(user));
            Button reset = Ui.iconButton(Material2OutlinedMZ.VPN_KEY, "Parolayı sıfırla");
            reset.setOnAction(e -> resetPassword(user));
            Button delete = Ui.iconButton(Material2OutlinedAL.DELETE, "Sil", Styles.DANGER);
            delete.setOnAction(e -> Dialogs.confirm("Kullanıcı silinsin mi?", user.fullName() + " silinecek.", "Sil",
                    Material2OutlinedAL.DELETE, true, () -> Async.run(() -> {
                        context.admin.deleteUser(user);
                        return true;
                    }, ok -> {
                        Toasts.success("Kullanıcı silindi.");
                        load();
                    })));
            setGraphic(Ui.row(2, edit, reset, delete));
        }
    }
}
