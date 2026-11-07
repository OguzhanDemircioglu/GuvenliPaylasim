package com.desktop.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ButtonBase;
import javafx.util.Duration;

/**
 * "Kod gönder" sonrası bekleme: kod 5 dakika geçerlidir (sunucu {@code code-ttl: 5m}); bu sürede düğme kalan süreyi
 * gösterir, süre bitince "Tekrar gönder" olur. Düğmenin kapalı olması {@link #running} ile bağlanır (düğmenin
 * kapalılığı başka koşullara da bağlı olduğu için burada doğrudan değiştirilmez).
 */
public final class Cooldown {

    public static final int CODE_SECONDS = 300;
    private static final String RUNNING = "cooldown.running";
    private static final String TIMELINE = "cooldown.timeline";

    private Cooldown() {
    }

    /** Geri sayım sürerken true. */
    public static BooleanProperty running(ButtonBase button) {
        return (BooleanProperty) button.getProperties().computeIfAbsent(RUNNING, k -> new SimpleBooleanProperty(false));
    }

    public static void start(ButtonBase button, int seconds) {
        start(button, seconds, false);
    }

    /** {@code compact}: dar yerlerde (alanın içindeki bağlantı) sadece kalan süre yazılır, ör. {@code 4:59}. */
    public static void start(ButtonBase button, int seconds, boolean compact) {
        if (button.getProperties().get(TIMELINE) instanceof Timeline previous) {
            previous.stop();
        }
        int[] left = {seconds};
        running(button).set(true);
        button.setText(label(left[0], compact));
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            left[0]--;
            if (left[0] > 0) {
                button.setText(label(left[0], compact));
            } else {
                button.setText("Tekrar gönder");
                running(button).set(false);
                button.getProperties().remove(TIMELINE);
            }
        }));
        timeline.setCycleCount(seconds);
        button.getProperties().put(TIMELINE, timeline);
        timeline.play();
    }

    private static String label(int seconds, boolean compact) {
        return compact ? format(seconds) : "Tekrar gönder (" + format(seconds) + ")";
    }

    /** {@code 299} → {@code 4:59}. */
    public static String format(int seconds) {
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
