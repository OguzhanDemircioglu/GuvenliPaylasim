package com.desktop.ui.views;

import atlantafx.base.theme.Styles;
import com.desktop.api.Dto;
import com.desktop.service.ContactService;
import com.desktop.ui.Async;
import com.desktop.ui.Dialogs;
import com.desktop.ui.Formats;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.List;

/**
 * Kişiler ve bekleyen davetler (Crypto_Design §7.6–7.8). Kişi eklemenin tek yolu davet kodudur: ilk kutu kodla
 * gönderilir, alıcı kodu girince iki taraf birbirinin listesine eklenir.
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

        VBox body = new VBox(18,
                Ui.row(8, Ui.label("Kişilerim", "section-title"), Ui.grow(), refresh), contacts,
                Ui.label("Bekleyen davetlerim", "section-title"),
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
            contacts.getChildren().setAll(Ui.emptyState(Material2OutlinedAL.CONTACTS, "Henüz kişiniz yok",
                    "Yeni Gönderi'de \"Davet kodu ile\" seçip birine ilk kutuyu gönderin. Kodu girdiğinde kişileriniz arasına eklenir."));
            return;
        }
        contacts.getChildren().setAll(list.stream().map(this::contactCard).toList());
    }

    private Node contactCard(ContactService.Contact contact) {
        Label chip = switch (contact.state()) {
            case VERIFIED -> Ui.chip("Doğrulandı", "success");
            case PENDING -> Ui.chip("Doğrulama bekleniyor", "accent");
            case KEY_CHANGED -> Ui.chip("Anahtarı değişti · yeni davet kodu gerekir", "warning");
            case UNTRUSTED -> Ui.chip("Kayıt doğrulanamadı", "danger");
        };
        Label fingerprint = Ui.muted(contact.fingerprint() == null ? "" : "Parmak izi: " + contact.fingerprint());

        Button delete = Ui.iconButton(Material2OutlinedMZ.PERSON_REMOVE, "Kişiyi sil", Styles.FLAT);
        delete.setOnAction(e -> Dialogs.confirm("Kişiyi sil",
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
        Button cancel = Ui.button("Geri al", Material2OutlinedAL.DELETE_OUTLINE, Styles.FLAT);
        cancel.setOnAction(e -> Async.run(() -> {
            context.invites.cancel(invite.id());
            return null;
        }, ignored -> {
            Toasts.success("Davet geri alındı; içeriği silindi.");
            load();
        }));
        VBox text = Ui.column(3, Ui.label(Formats.phone(invite.recipientPhone()), Styles.TEXT_BOLD),
                Ui.muted((invite.kind() == Dto.DocumentKind.TEXT ? "Şifreli yazı" : "Şifreli belge") + " · "
                        + Formats.size(invite.sizeBytes()) + " · son geçerlilik " + Formats.fullDate(invite.expiresAt())));
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox card = Ui.row(14, Ui.iconBadge(Material2OutlinedMZ.VPN_KEY, "accent", 40), text, cancel);
        card.getStyleClass().add("file-card");
        return card;
    }
}
