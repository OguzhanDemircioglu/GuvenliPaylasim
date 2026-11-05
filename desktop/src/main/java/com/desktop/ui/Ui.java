package com.desktop.ui;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/** Ekranlarda tekrar eden küçük yapı taşları. Görünüm app.css'tedir; burada sadece yapı kurulur. */
public final class Ui {

    private static final Color[] AVATAR_COLORS = {
            Color.web("#2f6fed"), Color.web("#0e9f6e"), Color.web("#c2410c"), Color.web("#7c3aed"),
            Color.web("#0891b2"), Color.web("#be185d"), Color.web("#4d7c0f")};

    private Ui() {
    }

    public static FontIcon icon(Ikon ikon, int size) {
        FontIcon icon = new FontIcon(ikon);
        icon.setIconSize(size);
        return icon;
    }

    public static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    public static Label muted(String text) {
        Label label = label(text, Styles.TEXT_MUTED);
        label.setWrapText(true);
        // Dar alanda "…" ile kesilmek yerine alt satıra geçer.
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    public static Button button(String text, Ikon ikon, String... styleClasses) {
        Button button = new Button(text, ikon == null ? null : icon(ikon, 16));
        button.getStyleClass().addAll(styleClasses);
        return button;
    }

    public static Button primary(String text, Ikon ikon) {
        return button(text, ikon, Styles.ACCENT);
    }

    public static Region grow() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public static HBox row(double spacing, Node... children) {
        HBox box = new HBox(spacing, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static VBox column(double spacing, Node... children) {
        return new VBox(spacing, children);
    }

    /** Kenarlıklı, gölgeli beyaz yüzey. */
    public static VBox surface(Node... children) {
        VBox box = new VBox(14, children);
        box.getStyleClass().add("surface");
        return box;
    }

    /** Ekran başlığı: büyük başlık ve altında açıklama. */
    public static VBox header(String title, String subtitle) {
        VBox box = new VBox(4, label(title, "page-title"), label(subtitle, "page-subtitle"));
        box.setPadding(new Insets(0, 0, 18, 0));
        return box;
    }

    /** Ad soyadın baş harfleri; renk isimden türetilir, aynı kişi hep aynı renkte görünür. */
    public static StackPane avatar(String fullName, double radius) {
        Circle circle = new Circle(radius, AVATAR_COLORS[Math.floorMod(fullName.hashCode(), AVATAR_COLORS.length)]);
        Label initials = label(Formats.initials(fullName), "avatar-text");
        initials.setStyle("-fx-font-size: " + Math.round(radius * 0.78) + "px;");
        StackPane avatar = new StackPane(circle, initials);
        avatar.setMinSize(radius * 2, radius * 2);
        avatar.setMaxSize(radius * 2, radius * 2);
        return avatar;
    }

    /** Renkli zemin üzerinde ikon (özellik kartları ve boş durumlar için). */
    public static StackPane iconBadge(Ikon ikon, String tone, double size) {
        StackPane badge = new StackPane(icon(ikon, (int) (size * 0.5)));
        badge.getStyleClass().addAll("icon-badge", "tone-" + tone);
        badge.setMinSize(size, size);
        badge.setMaxSize(size, size);
        return badge;
    }

    /** Etiket kısaltılmaz; içinde bulunduğu satır daralırsa diğer öğeler küçülür. */
    public static Label chip(String text, String tone) {
        Label chip = label(text, "chip", "tone-" + tone);
        chip.setMinWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    /** Sadece ikondan oluşan düğme; ne yaptığı üzerine gelince görünür. */
    public static Button iconButton(Ikon ikon, String tooltip, String... styleClasses) {
        Button button = button(null, ikon, Styles.BUTTON_ICON, Styles.FLAT);
        button.getStyleClass().addAll(styleClasses);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    /** Liste boşken gösterilen ortalanmış ikon ve açıklama. */
    public static VBox emptyState(Ikon ikon, String title, String description) {
        Label desc = muted(description);
        desc.setMaxWidth(320);
        desc.setAlignment(Pos.CENTER);
        desc.setStyle("-fx-text-alignment: center;");
        VBox box = new VBox(10, iconBadge(ikon, "accent", 64), label(title, Styles.TITLE_4), desc);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        return box;
    }
}
