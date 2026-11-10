package com.desktop.ui.views;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.desktop.ui.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

/**
 * "Neden güvenli?" — eski sistemle (Güvenli Bilgi Paylaşım Sistemi kılavuzu, 2016) karşılaştırma, belgenin
 * yolculuğu ve sunucunun neyi görüp neyi göremediği.
 */
final class WhyView extends ScrollPane {

    private record Comparison(String topic, String before, String after) {
    }

    private static final Comparison[] COMPARISONS = {
            new Comparison("Anahtar Paylaşımı",
                    "İki anahtar alıcılara SMS ile açık metin olarak gönderilir.",
                    "Paylaşılan şifre yoktur. Her kişinin kendi anahtar çifti vardır."),
            new Comparison("Şifreleme Anahtarı",
                    "Kullanıcının yazdığı anahtarlar (ör. \"123\") kullanılır.",
                    "Her belgeye rastgele üretilen 256 bitlik AES-GCM anahtarı."),
            new Comparison("Alıcıya Özel Erişim",
                    "Aynı anahtarlar seçilen bütün birimlere gider.",
                    "Belge anahtarı her alıcı için ayrı ayrı sarmalanır."),
            new Comparison("Gönderen Doğrulaması",
                    "Belgenin kimden geldiği kanıtlanamaz.",
                    "Her belge Ed25519 ile imzalanır; sahte ya da değiştirilmiş belge açılmaz."),
            new Comparison("Sunucu Ele Geçirilirse",
                    "Veritabanı ve SMS'ler birlikte belgeleri açmaya yeter.",
                    "Sunucuda sadece şifreli veri vardır; açacak anahtar sunucuda yoktur."),
            new Comparison("Şifre",
                    "Şifre sunucuya gönderilir.",
                    "Şifre bilgisayardan çıkmaz; sunucuya ondan türetilen bir değer gider."),
            new Comparison("Silme",
                    "Okunan belge isteğe bağlı silinir.",
                    "Son alıcı da silince şifreli içerik veritabanından kalıcı olarak silinir."),
            new Comparison("Güncelleme",
                    "Her bilgisayara elle kurulur.",
                    "İmzası doğrulanan güncellemeler otomatik gelir ve zorunludur."),
    };

    WhyView() {
        VBox page = new VBox(22, hero(), comparison(), journey(), serverView());
        page.getStyleClass().add("page");
        setContent(page);
        setFitToWidth(true);
        getStyleClass().add(Tweaks.EDGE_TO_EDGE);
        setStyle("-fx-background-color: transparent; -fx-background: transparent;");
    }

    private static Node hero() {
        Label title = Ui.label("Belgeyi sadece muhatabı açabilir.", "hero-title");
        Label text = Ui.label("Lockpost gerçek asimetrik şifreleme kullanır: belgeler gönderenin bilgisayarında "
                + "şifrelenir ve imzalanır, sadece alıcının bilgisayarında çözülür. Arada kimse, sunucuyu yöneten "
                + "dahil, içeriği göremez.", "hero-text");
        text.setWrapText(true);
        text.setMaxWidth(720);

        HBox stats = Ui.row(28,
                stat("0", "SMS'le gönderilen şifre"),
                stat("256 bit", "belgeye özel anahtar"),
                stat("1", "alıcı başına sarmalanmış anahtar"),
                stat("600.000", "tur şifre koruması"));
        VBox hero = new VBox(14, Ui.label("NEDEN GÜVENLİ?", "hero-feature-text"), title, text, stats);
        hero.getStyleClass().add("why-hero");
        return hero;
    }

    private static Node stat(String value, String label) {
        Label number = Ui.label(value, "stat-value");
        number.setStyle("-fx-text-fill: white;");
        return Ui.column(0, number, Ui.label(label, "hero-feature-text"));
    }

    private static Node comparison() {
        GridPane grid = new GridPane();
        ColumnConstraints topic = new ColumnConstraints(230);
        ColumnConstraints before = new ColumnConstraints();
        before.setPercentWidth(38);
        ColumnConstraints after = new ColumnConstraints();
        after.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(topic, before, after);
        grid.setHgap(18);

        grid.addRow(0, Ui.label("KONU", "compare-header"), Ui.label("ESKİ SİSTEM", "compare-header"),
                Ui.label("LOCKPOST", "compare-header"));
        for (int i = 0; i < COMPARISONS.length; i++) {
            Comparison c = COMPARISONS[i];
            HBox row = new HBox();
            row.getStyleClass().add("compare-row");
            grid.add(row, 0, i + 1, 3, 1);
            grid.add(cell(Ui.label(c.topic(), "compare-topic")), 0, i + 1);
            grid.add(cell(way(Material2OutlinedAL.CANCEL, c.before(), "old-way")), 1, i + 1);
            grid.add(cell(way(Material2OutlinedAL.CHECK_CIRCLE, c.after(), "new-way")), 2, i + 1);
        }
        return Ui.surface(Ui.label("Eski Sistemle Karşılaştırma", "section-title"),
                Ui.muted("Eski sistemin davranışı kullanıcı kılavuzundan alınmıştır."), grid);
    }

    private static Node cell(Node content) {
        VBox box = new VBox(content);
        box.setStyle("-fx-padding: 12 0 12 0;");
        return box;
    }

    private static Node way(Ikon ikon, String text, String styleClass) {
        Label label = new Label(text, Ui.icon(ikon, 18));
        label.setWrapText(true);
        label.setGraphicTextGap(10);
        label.getStyleClass().add(styleClass);
        label.setAlignment(Pos.TOP_LEFT);
        return label;
    }

    private static Node journey() {
        HBox flow = Ui.row(8,
                flowStep(Material2OutlinedAL.EDIT, "1 · Siz", "Belgeyi ve alıcı kişileri seçersiniz."),
                arrow(),
                flowStep(Material2OutlinedAL.ENHANCED_ENCRYPTION, "2 · Bu bilgisayar",
                        "Rastgele anahtarla şifreler, imzalar, anahtarı her alıcıya ayrı sarmalar."),
                arrow(),
                flowStep(Material2OutlinedAL.DNS, "3 · Sunucu", "Sadece şifreli veriyi saklar. İçeriği göremez."),
                arrow(),
                flowStep(Material2OutlinedMZ.VERIFIED_USER, "4 · Alıcı", "İmzayı doğrular, kendi anahtarıyla çözer."),
                arrow(),
                flowStep(Material2OutlinedAL.DELETE_FOREVER, "5 · Silme", "Herkes silince içerik kalıcı olarak yok olur."));
        flow.getChildren().filtered(n -> n instanceof VBox).forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));
        return Ui.surface(Ui.label("Bir Belgenin Yolculuğu", "section-title"), flow);
    }

    private static Node flowStep(Ikon ikon, String title, String text) {
        Label body = Ui.muted(text);
        VBox step = new VBox(8, Ui.iconBadge(ikon, "accent", 40), Ui.label(title, Styles.TEXT_BOLD), body);
        step.getStyleClass().add("flow-step");
        step.setPrefWidth(170);
        step.setMaxWidth(Double.MAX_VALUE);
        return step;
    }

    private static Node arrow() {
        Node icon = Ui.icon(Material2OutlinedAL.ARROW_FORWARD, 20);
        icon.getStyleClass().add("flow-arrow");
        return icon;
    }

    private static Node serverView() {
        VBox sees = list(Material2OutlinedMZ.VISIBILITY, "success", "Sunucunun Gördükleri",
                "Kimin kime ne zaman belge gönderdiği", "Belgenin Şifreli Halinin Boyutu",
                "Kullanıcıların Açık Anahtarları", "Okundu ve Silindi Bilgisi");
        VBox cannot = list(Material2OutlinedMZ.VISIBILITY_OFF, "danger", "Sunucunun Göremedikleri",
                "Belgenin içeriği ve dosya adı", "Belge Anahtarları", "Kullanıcıların Özel Anahtarları",
                "Kullanıcıların Şifreleri");
        HBox.setHgrow(sees, Priority.ALWAYS);
        HBox.setHgrow(cannot, Priority.ALWAYS);
        return new HBox(20, sees, cannot);
    }

    private static VBox list(Ikon ikon, String tone, String title, String... lines) {
        VBox items = new VBox(10);
        for (String line : lines) {
            Label item = new Label(line, Ui.icon(tone.equals("success") ? Material2OutlinedAL.CHECK : Material2OutlinedAL.BLOCK, 16));
            item.setGraphicTextGap(10);
            items.getChildren().add(item);
        }
        return Ui.surface(Ui.row(12, Ui.iconBadge(ikon, tone, 40), Ui.label(title, "section-title")), items);
    }
}
