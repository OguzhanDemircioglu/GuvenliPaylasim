package com.desktop.ui;

import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import com.desktop.api.ApiException;
import com.desktop.crypto.CryptoException;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;

/** Sağ üst köşede birkaç saniye görünen bildirimler. */
public final class Toasts {

    private static VBox layer;

    private Toasts() {
    }

    static void install(VBox toastLayer) {
        layer = toastLayer;
        layer.setAlignment(Pos.TOP_RIGHT);
        layer.setPadding(new Insets(20));
        layer.setSpacing(10);
        layer.setPickOnBounds(false);
    }

    public static void success(String message) {
        show(message, Material2OutlinedAL.CHECK_CIRCLE, Styles.SUCCESS, Duration.seconds(4));
    }

    public static void info(String message) {
        show(message, Material2OutlinedAL.INFO, Styles.ACCENT, Duration.seconds(6));
    }

    public static void error(Throwable error) {
        show(describe(error), Material2OutlinedAL.ERROR_OUTLINE, Styles.DANGER, Duration.seconds(7));
    }

    /** Kullanıcıya gösterilecek metin; teknik ayrıntı (sınıf adı, stacktrace) gösterilmez. */
    public static String describe(Throwable error) {
        return error instanceof ApiException || error instanceof CryptoException
                || error instanceof IllegalArgumentException
                ? error.getMessage()
                : "Beklenmeyen bir hata oluştu (" + error.getClass().getSimpleName() + ").";
    }

    private static void show(String message, Ikon ikon, String tone, Duration visible) {
        if (layer == null) {
            return;
        }
        Notification notification = new Notification(message, Ui.icon(ikon, 20));
        notification.getStyleClass().addAll(tone, Styles.ELEVATED_2);
        notification.setPrefWidth(380);
        notification.setMaxWidth(380);
        notification.setMaxHeight(Notification.USE_PREF_SIZE);
        notification.setOnClose(e -> layer.getChildren().remove(notification));
        layer.getChildren().add(notification);

        TranslateTransition slide = new TranslateTransition(Duration.millis(220), notification);
        slide.setFromX(40);
        slide.setToX(0);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), notification);
        fadeOut.setToValue(0);
        SequentialTransition life = new SequentialTransition(slide, new PauseTransition(visible), fadeOut);
        life.setOnFinished(e -> layer.getChildren().remove(notification));
        life.play();
    }
}
