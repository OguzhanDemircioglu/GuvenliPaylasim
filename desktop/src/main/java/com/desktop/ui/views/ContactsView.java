package com.desktop.ui.views;

import atlantafx.base.theme.Styles;
import com.desktop.api.Dto;
import com.desktop.crypto.DocumentContent;
import com.desktop.service.AuthService;
import com.desktop.service.ContactService;
import com.desktop.ui.Async;
import com.desktop.ui.Dialogs;
import com.desktop.ui.Formats;
import com.desktop.ui.Inputs;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.List;

/**
 * Kişiler ve bekleyen davetler (Crypto_Design §7.6–7.8). Kişi eklemenin tek yolu davet kodudur: ilk kutu kodla
 * gönderilir, alıcı kodu girince iki taraf birbirinin listesine eklenir. "Kişi Ekle" aynı daveti belge seçmeden,
 * kısa bir tanışma yazısıyla gönderir; kod, sunucunun araya sahte anahtar koymasını önler (Security S3).
 */
final class ContactsView extends VBox {

    private final Context context;
    private final VBox contacts = new VBox(10);
    private final VBox invites = new VBox(10);

    ContactsView(Context context) {
        this.context = context;
        getStyleClass().add("page");

        Button refresh = Ui.button("Yenile", Material2OutlinedMZ.REFRESH, Styles.FLAT);
        refresh.setOnAction(e -> load());
        Button add = Ui.primary("Kişi Ekle", Material2OutlinedMZ.PERSON_ADD);
        add.setOnAction(e -> openAddContact());

        VBox body = new VBox(18,
                Ui.row(8, Ui.label("Kişilerim", "section-title"), Ui.grow(), refresh, add), contacts,
                Ui.label("Bekleyen Davetlerim", "section-title"),
                Ui.muted("Kodu henüz girilmemiş davetler. Süresi dolan davet içeriğiyle birlikte kalıcı olarak silinir."),
                invites);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(Ui.header("Kişiler",
                "Kodsuz gönderim sadece kodla doğrulanmış kişilere yapılır. Anahtarı değişen kişiye yeniden davet kodu gerekir."),
                scroll);
        load();
    }

    private void load() {
        Async.run(context.contacts::list, this::showContacts);
        Async.run(context.invites::sent, this::showInvites);
    }

    private void showContacts(List<ContactService.Contact> list) {
        if (list.isEmpty()) {
            contacts.getChildren().setAll(Ui.emptyState(Material2OutlinedAL.CONTACTS, "Henüz Kişiniz Yok",
                    "\"Kişi Ekle\" ile bir davet kodu oluşturun ve kodu kişiye iletin. Kodu girdiğinde kişileriniz arasına eklenir."));
            return;
        }
        contacts.getChildren().setAll(list.stream().map(this::contactCard).toList());
    }

    private record Validity(String label, int hours) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** Kişi ekleme: telefon + kodun geçerlilik süresi; davet kısa bir tanışma yazısıyla gönderilir. */
    private void openAddContact() {
        TextField phone = Inputs.phone(new TextField());
        phone.setPromptText("(5xx) xxx xx xx");
        ComboBox<Validity> validity = new ComboBox<>();
        validity.getItems().setAll(new Validity("1 saat", 1), new Validity("1 gün", 24), new Validity("3 gün", 72),
                new Validity("7 gün", 168));
        validity.getSelectionModel().select(1);
        validity.setMaxWidth(Double.MAX_VALUE);
        Label help = Ui.muted("Bir davet kodu oluşturulur. Kodu kişiye kendi kanalınızla (yüz yüze, telefonla) iletin; "
                + "kişi kodu \"Kod ile Al\"a girince ikiniz de birbirinizin kişileri arasına eklenirsiniz. Kişi kayıtlı "
                + "değilse bu sürede kayıt olabilir. Kod, sunucunun bile araya girememesini sağlar.");
        help.setWrapText(true);
        help.setMaxWidth(440);

        SimpleBooleanProperty busy = new SimpleBooleanProperty(false);
        Button cancel = Ui.button("Vazgeç", null);
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> Dialogs.close());
        Button create = Ui.primary("Kod Oluştur", Material2OutlinedMZ.VPN_KEY);
        create.setDefaultButton(true);
        create.disableProperty().bind(busy.or(Bindings.createBooleanBinding(
                () -> AuthService.validatePhone(phone.getText()) != null, phone.textProperty())));
        create.setOnAction(e -> {
            busy.set(true);
            String number = phone.getText();
            int hours = validity.getValue().hours();
            String greeting = "Merhaba, sizi Güvenli Paylaşım'da kişi olarak ekledim.\n— " + context.session.fullName();
            // Kutunun ömrü (7 gün) kodun geçerlilik süresinden kısa olamaz.
            Async.run(() -> context.invites.send(DocumentContent.ofText(greeting), number, hours, 168, false),
                    sent -> {
                        // Açık diyaloğun yerine geçer; önce kapatmak kapanma animasyonuyla yenisini de gizler.
                        InviteCodeDialog.show(sent);
                        load();
                    },
                    failure -> {
                        busy.set(false);
                        Toasts.error(failure);
                    });
        });
        Dialogs.show("Kişi Ekle", new VBox(10, help, Ui.label("Cep Telefonu", Styles.TEXT_BOLD), phone,
                Ui.label("Kod Geçerlilik Süresi", Styles.TEXT_BOLD), validity), cancel, create);
        phone.requestFocus();
    }

    private Node contactCard(ContactService.Contact contact) {
        Label chip = switch (contact.state()) {
            case VERIFIED -> Ui.chip("Doğrulandı", "success");
            case PENDING -> Ui.chip("Doğrulama Bekleniyor", "accent");
            case KEY_CHANGED -> Ui.chip("Anahtarı değişti · yeni davet kodu gerekir", "warning");
            case UNTRUSTED -> Ui.chip("Kayıt Doğrulanamadı", "danger");
        };
        Label fingerprint = Ui.muted(contact.fingerprint() == null ? "" : "Parmak izi: " + contact.fingerprint());

        Button delete = Ui.iconButton(Material2OutlinedMZ.PERSON_REMOVE, "Kişiyi Sil", Styles.FLAT);
        delete.setOnAction(e -> Dialogs.confirm("Kişiyi Sil",
                contact.fullName() + " iki taraflı olarak silinir. Tekrar göndermek için yeni davet kodu gerekir.",
                "Sil", Material2OutlinedMZ.PERSON_REMOVE, true,
                () -> Async.run(() -> {
                    context.contacts.delete(contact.userId());
                    return null;
                }, ignored -> {
                    Toasts.success(contact.fullName() + " kişilerinizden silindi.");
                    load();
                })));

        VBox text = Ui.column(3, Ui.row(8, Ui.label(contact.fullName(), Styles.TEXT_BOLD), chip),
                Ui.muted(Formats.phone(contact.phone())), fingerprint);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox card = Ui.row(14, Ui.avatar(contact.fullName(), 20), text, delete);
        card.getStyleClass().add("file-card");
        return card;
    }

    private void showInvites(List<Dto.SentInvite> list) {
        if (list.isEmpty()) {
            invites.getChildren().setAll(Ui.muted("Bekleyen davetiniz yok."));
            return;
        }
        invites.getChildren().setAll(list.stream().map(this::inviteCard).toList());
    }

    private Node inviteCard(Dto.SentInvite invite) {
        Button cancel = Ui.button("Geri Al", Material2OutlinedAL.DELETE_OUTLINE, Styles.FLAT);
        cancel.setOnAction(e -> Async.run(() -> {
            context.invites.cancel(invite.id());
            return null;
        }, ignored -> {
            Toasts.success("Davet geri alındı; içeriği silindi.");
            load();
        }));
        VBox text = Ui.column(3, Ui.label(Formats.phone(invite.recipientPhone()), Styles.TEXT_BOLD),
                Ui.muted((invite.kind() == Dto.DocumentKind.TEXT ? "Şifreli Yazı" : "Şifreli Belge") + " · "
                        + Formats.size(invite.sizeBytes()) + " · son geçerlilik " + Formats.fullDate(invite.expiresAt())));
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox card = Ui.row(14, Ui.iconBadge(Material2OutlinedMZ.VPN_KEY, "accent", 40), text, cancel);
        card.getStyleClass().add("file-card");
        return card;
    }
}
