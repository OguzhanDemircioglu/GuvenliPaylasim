package com.desktop.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.ButtonBase;
import javafx.util.Duration;

/**
 * "Kod gönder" sonrası bekleme: kod 5 dakika geçerlidir (sunucu {@code code-ttl: 5m}); bu sürede düğme kapalı kalır
 * ve kalan süreyi gösterir, süre bitince "Tekrar gönder" olur.
 */
public final class Cooldown {

    public static final int CODE_SECONDS = 300;

    private Cooldown() {
    }

    public static void start(ButtonBase button, int seconds) {
        start(button, seconds, false);
    }

    /** {@code compact}: dar yerlerde (alanın içindeki bağlantı) sadece kalan süre yazılır, ör. {@code 4:59}. */
    public static void start(ButtonBase button, int seconds, boolean compact) {
        if (button.getProperties().get(Cooldown.class) instanceof Timeline previous) {
            previous.stop();
        }
        int[] left = {seconds};
        button.setDisable(true);
        button.setText(compact ? format(left[0]) : "Tekrar gönder (" + format(left[0]) + ")");
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            left[0]--;
            if (left[0] > 0) {
                button.setText(compact ? format(left[0]) : "Tekrar gönder (" + format(left[0]) + ")");
            } else {
                button.setText("Tekrar gönder");
                button.setDisable(false);
                button.getProperties().remove(Cooldown.class);
            }
        }));
        timeline.setCycleCount(seconds);
        button.getProperties().put(Cooldown.class, timeline);
        timeline.play();
    }

    /** {@code 299} → {@code 4:59}. */
    public static String format(int seconds) {
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
