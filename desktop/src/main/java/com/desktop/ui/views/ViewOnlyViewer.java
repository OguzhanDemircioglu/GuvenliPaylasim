package com.desktop.ui.views;

import com.desktop.crypto.DocumentContent;
import com.desktop.service.FileTypes;
import com.desktop.ui.Async;
import com.desktop.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * "Kaydedilemez" belgeyi uygulama içinde gösterir (UI_Standards §4.6). İçerik seçilemez ve kopyalanamaz: yazı
 * seçilemeyen bir etikette, resim ve PDF sayfaları resim olarak gösterilir. Kaydetme düğmesi yoktur.
 */
final class ViewOnlyViewer {

    /** Bellek için üst sınır: bir sayfa ~4 MB yer kaplar. */
    private static final int MAX_PDF_PAGES = 40;
    private static final float PDF_SCALE = 1.25f;

    private ViewOnlyViewer() {
    }

    static Node create(DocumentContent content) {
        if (content.text()) {
            Label text = new Label(content.asText());
            text.setWrapText(true);
            text.getStyleClass().add("view-only-text");
            return scroll(text);
        }
        String ext = FileTypes.extension(content.fileName());
        if (ext.equals("pdf")) {
            return pdf(content.data());
        }
        if (FileTypes.viewable(content.fileName())) {
            Image image = new Image(new ByteArrayInputStream(content.data()));
            if (image.isError()) {
                return message("Resim gösterilemedi; dosya bozuk olabilir.");
            }
            return scroll(imageView(image));
        }
        return message("Bu dosya türü uygulama içinde gösterilemiyor. Gönderen kaydetmeye izin vermediği için "
                + "kaydedilemez.");
    }

    private static Node pdf(byte[] data) {
        VBox pages = new VBox(12);
        pages.setAlignment(Pos.TOP_CENTER);
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(32, 32);
        pages.getChildren().add(spinner);
        ScrollPane scroll = scroll(pages);
        Async.run(() -> renderPdf(data), rendered -> {
            pages.getChildren().clear();
            for (Image page : rendered.images()) {
                pages.getChildren().add(imageView(page));
            }
            if (rendered.total() > rendered.images().size()) {
                pages.getChildren().add(Ui.muted("İlk " + rendered.images().size() + " sayfa gösteriliyor (toplam "
                        + rendered.total() + ")."));
            }
        }, failure -> pages.getChildren().setAll(Ui.muted("PDF gösterilemedi; dosya bozuk ya da şifreli olabilir.")));
        return scroll;
    }

    private record Rendered(List<Image> images, int total) {
    }

    private static Rendered renderPdf(byte[] data) throws java.io.IOException {
        try (PDDocument document = Loader.loadPDF(data)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int total = document.getNumberOfPages();
            List<Image> images = new ArrayList<>();
            for (int i = 0; i < Math.min(total, MAX_PDF_PAGES); i++) {
                images.add(toFx(renderer.renderImage(i, PDF_SCALE, ImageType.RGB)));
            }
            return new Rendered(images, total);
        }
    }

    private static Image toFx(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] argb = source.getRGB(0, 0, width, height, null, 0, width);
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        return image;
    }

    /** Resim alana sığacak kadar küçültülür, büyütülmez. */
    private static ImageView imageView(Image image) {
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null && view.getParent() != null) {
                ScrollPane scroll = enclosingScroll(view);
                if (scroll != null) {
                    view.fitWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                            () -> Math.min(image.getWidth(), Math.max(50, scroll.getViewportBounds().getWidth() - 24)),
                            scroll.viewportBoundsProperty()));
                }
            }
        });
        return view;
    }

    private static ScrollPane enclosingScroll(Node node) {
        for (Node n = node.getParent(); n != null; n = n.getParent()) {
            if (n instanceof ScrollPane scroll) {
                return scroll;
            }
        }
        return null;
    }

    private static ScrollPane scroll(Node content) {
        VBox box = new VBox(content);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("view-only");
        return scroll;
    }

    private static Node message(String text) {
        Label label = Ui.muted(text);
        label.setWrapText(true);
        return label;
    }
}
