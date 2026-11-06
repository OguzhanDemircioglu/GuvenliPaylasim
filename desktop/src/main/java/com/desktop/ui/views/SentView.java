package com.desktop.ui.views;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.desktop.api.Dto;
import com.desktop.ui.Async;
import com.desktop.ui.Formats;
import com.desktop.ui.Ui;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.List;

/**
 * Gönderilen belgeler ve her alıcının durumu. Gönderen belgeyi açamaz (anahtar sadece alıcılar için
 * sarmalanmıştır); burada sadece kimin okuduğu ve sildiği görünür.
 */
final class SentView extends VBox {

    private final Context context;
    private final VBox items = new VBox(12);

    SentView(Context context) {
        this.context = context;
        getStyleClass().add("page");

        Button refresh = Ui.button("Yenile", Material2OutlinedMZ.REFRESH, Styles.FLAT);
        refresh.setOnAction(e -> load());
        HBox top = Ui.row(10, Ui.header("Gönderilenler",
                "Ömrü dolmamış kutularınız ve okunma durumu. Silinen kutu bu listeden de kalkar."), Ui.grow(), refresh);

        ScrollPane scroll = new ScrollPane(items);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add(Tweaks.EDGE_TO_EDGE);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(top, scroll);
        load();
    }

    private void load() {
        Async.run(context.documents::sent, this::render);
    }

    private void render(List<Dto.SentItem> sent) {
        if (sent.isEmpty()) {
            items.getChildren().setAll(Ui.emptyState(Material2OutlinedMZ.SEND, "Henüz belge göndermediniz",
                    "Yeni Gönderi ekranından şifreli yazı ya da belge gönderebilirsiniz."));
            return;
        }
        items.getChildren().setAll(sent.stream().map(this::card).toList());
    }

    private Node card(Dto.SentItem item) {
        boolean text = item.kind() == Dto.DocumentKind.TEXT;
        long read = item.recipients().stream().filter(r -> r.readAt() != null).count();
        Label title = Ui.label((text ? "Şifreli yazı" : "Şifreli belge") + " · No " + item.id(), Styles.TEXT_BOLD);
        Label meta = Ui.muted(Formats.fullDate(item.createdAt()) + " · " + Formats.size(item.sizeBytes())
                + " · " + Formats.remaining(item.expiresAt()) + " sonra silinir"
                + (item.deleteOnOpen() ? " · açılınca silinir" : ""));
        Node status = Ui.chip(read + " / " + item.recipients().size() + " okundu",
                read == item.recipients().size() ? "success" : "accent");

        FlowPane recipients = new FlowPane(8, 8);
        item.recipients().forEach(r -> recipients.getChildren().add(recipientChip(r)));

        HBox header = Ui.row(14, Ui.iconBadge(text ? Material2OutlinedMZ.TEXT_SNIPPET : Material2OutlinedAL.ATTACH_FILE,
                "accent", 40), Ui.column(2, title, meta), Ui.grow(), status);
        return Ui.surface(header, recipients);
    }

    private Node recipientChip(Dto.SentRecipient recipient) {
        String tone;
        String state;
        if (recipient.readAt() != null) {
            tone = "success";
            state = "okudu";
        } else {
            tone = "warning";
            state = "bekliyor";
        }
        Label chip = Ui.chip(recipient.fullName() + " · " + state, tone);
        if (recipient.readAt() != null) {
            chip.setTooltip(new Tooltip("Okundu: " + Formats.fullDate(recipient.readAt())));
        }
        return chip;
    }
}
