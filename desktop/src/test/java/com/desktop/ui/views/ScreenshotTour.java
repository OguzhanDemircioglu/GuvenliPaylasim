package com.desktop.ui.views;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.desktop.App;
import com.desktop.service.AuthService;
import com.desktop.service.Session;
import com.desktop.ui.Overlays;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Geliştirme aracı: çalışan dev sunucusuna demo hesaplarıyla bağlanır ve her ekranın görüntüsünü PNG olarak
 * kaydeder. Tasarımı pencere açmadan gözden geçirmek için.
 * <pre>java -cp ... com.desktop.ui.views.ScreenshotTour &lt;çıktı klasörü&gt; [dark]</pre>
 */
public final class ScreenshotTour {

    /** Sunucudaki V001_DemoData ile aynı. */
    private static final String PASSWORD = "DemoParola2026!";
    private static Path out;
    private static boolean dark;

    public static void main(String[] args) throws Exception {
        out = Path.of(args[0]);
        dark = args.length > 1 && args[1].equals("dark");
        Files.createDirectories(out);

        AuthService auth = new AuthService("http://localhost:8080");
        Session ayse = auth.login("5550000001", PASSWORD.toCharArray());
        Session mehmet = auth.login("5550000002", PASSWORD.toCharArray());
        // Belge gönderimi kişiler ve davet kodu (kodlama sırasının 4. adımı) gelince bu tura geri eklenir.

        Platform.startup(() -> {
        });
        Application.setUserAgentStylesheet(dark ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        String suffix = dark ? "-dark" : "";

        StackPane content = new StackPane();
        Stage stage = onFx(() -> {
            ModalPane modal = new ModalPane();
            VBox toasts = new VBox();
            StackPane root = new StackPane(content, modal, toasts);
            Overlays.install(modal, toasts);
            Scene scene = new Scene(root, 1320, 840);
            scene.getStylesheets().add(App.class.getResource("/com/desktop/app.css").toExternalForm());
            Stage s = new Stage();
            s.setScene(scene);
            s.show();
            return s;
        });

        LoginView login = onFx(() -> new LoginView(auth, s -> {
        }));
        show(content, () -> login);
        snap(stage, "01-giris" + suffix);
        onFx(() -> {
            login.lookupAll(".hyperlink").stream()
                    .filter(n -> n instanceof javafx.scene.control.Hyperlink h && "Kayıt ol".equals(h.getText()))
                    .findFirst().ifPresent(n -> ((javafx.scene.control.Hyperlink) n).fire());
            return null;
        });
        pause(3200);
        snap(stage, "01b-kayit" + suffix);
        onFx(() -> {
            login.lookupAll(".hyperlink").stream()
                    .filter(n -> n instanceof javafx.scene.control.Hyperlink h && "Giriş yap".equals(h.getText()))
                    .findFirst().ifPresent(n -> ((javafx.scene.control.Hyperlink) n).fire());
            return null;
        });
        pause(3200);
        onFx(() -> {
            login.lookupAll(".hyperlink").stream()
                    .filter(n -> n instanceof javafx.scene.control.Hyperlink h && "Parolamı unuttum".equals(h.getText()))
                    .findFirst().ifPresent(n -> ((javafx.scene.control.Hyperlink) n).fire());
            return null;
        });
        pause(800);
        snap(stage, "01c-parola-sifirlama" + suffix);
        onFx(() -> {
            com.desktop.ui.Dialogs.close();
            return null;
        });

        ShellView shell = onFx(() -> new ShellView(new App(), auth, ayse));
        show(content, () -> shell);
        pause(1500);
        snap(stage, "02-gelen-kutusu" + suffix);

        nav(shell, 1);
        pause(1200);
        snap(stage, "05-yeni-gonderi" + suffix);
        nav(shell, 3);
        pause(800);
        snap(stage, "06-anahtarim" + suffix);
        nav(shell, 4);
        pause(800);
        snap(stage, "07-neden-guvenli" + suffix);

        ShellView mehmetShell = onFx(() -> new ShellView(new App(), auth, mehmet));
        show(content, () -> mehmetShell);
        pause(1200);
        nav(mehmetShell, 2);
        pause(1200);
        snap(stage, "08-gonderilenler" + suffix);

        Platform.exit();
        System.exit(0);
    }

    private static void nav(Node shell, int index) throws Exception {
        onFx(() -> {
            List<Node> buttons = shell.lookupAll(".nav-button").stream().toList();
            ((ToggleButton) buttons.get(index)).fire();
            return null;
        });
    }

    private static void show(StackPane content, Supplier<Node> view) throws Exception {
        onFx(() -> {
            content.getChildren().setAll(view.get());
            return null;
        });
    }

    private static void snap(Stage stage, String name) throws Exception {
        pause(300);
        WritableImage image = onFx(() -> stage.getScene().snapshot(null));
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        ImageIO.write(png, "png", out.resolve(name + ".png").toFile());
        System.out.println("saved " + name);
    }

    private static void pause(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private static <T> T onFx(Supplier<T> work) throws Exception {
        Object[] result = new Object[1];
        Throwable[] error = new Throwable[1];
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result[0] = work.get();
            } catch (Throwable e) {
                error[0] = e;
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(20, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX timeout");
        }
        if (error[0] != null) {
            throw new RuntimeException(error[0]);
        }
        @SuppressWarnings("unchecked")
        T value = (T) result[0];
        return value;
    }
}
