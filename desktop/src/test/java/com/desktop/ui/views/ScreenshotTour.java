package com.desktop.ui.views;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.desktop.App;
import com.desktop.api.Dto;
import com.desktop.crypto.DocumentContent;
import com.desktop.service.AuthService;
import com.desktop.service.ContactService;
import com.desktop.service.DocumentService;
import com.desktop.service.InviteService;
import com.desktop.service.Session;
import com.desktop.ui.Overlays;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
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

        // Mehmet ile Ayşe kişi değilse önce davet koduyla tanıştırılır (Crypto_Design §7.6), sonra kodsuz gönderilir.
        ContactService mehmetContacts = new ContactService(mehmet);
        if (mehmetContacts.list().stream().noneMatch(c -> c.canReceive() && c.userId().equals(ayse.user().id()))) {
            InviteService.SentCode code = new InviteService(ayse).send(
                    DocumentContent.ofText("Merhaba Mehmet Bey, bundan sonra belgeleri buradan paylaşalım."), "5550000002",
                    24, 168, false);
            new InviteService(mehmet).redeem(code.code());
            new InviteService(ayse).processVerifications();
        }
        List<Dto.Recipient> toAyse = mehmetContacts.list().stream()
                .filter(c -> c.canReceive() && c.userId().equals(ayse.user().id()))
                .map(ContactService.Contact::recipient).toList();
        DocumentService fromMehmet = new DocumentService(mehmet);
        fromMehmet.send(DocumentContent.ofText("""
                Sayın Ayşe Hanım,

                14 Kasım tarihli koordinasyon toplantısının gündemi ve katılımcı listesi ektedir. Toplantı saat 14:00'te yapılacaktır.

                Bilgilerinize rica ederim.
                Mehmet Kaya"""), toAyse, 168, false);
        fromMehmet.send(DocumentContent.ofFile("Denetim Raporu 2026.pdf", "application/pdf", new byte[180_000]), toAyse, 24, true);

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
            Scene scene = new Scene(root, App.MAIN_WIDTH, App.MAIN_HEIGHT);
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
                    .filter(n -> n instanceof javafx.scene.control.Hyperlink h && "Kayıt Ol".equals(h.getText()))
                    .findFirst().ifPresent(n -> ((javafx.scene.control.Hyperlink) n).fire());
            return null;
        });
        pause(3200);
        snap(stage, "01b-kayit" + suffix);
        onFx(() -> {
            login.lookupAll(".hyperlink").stream()
                    .filter(n -> n instanceof javafx.scene.control.Hyperlink h && "Giriş Yap".equals(h.getText()))
                    .findFirst().ifPresent(n -> ((javafx.scene.control.Hyperlink) n).fire());
            return null;
        });
        pause(3200);
        onFx(() -> {
            login.lookupAll(".hyperlink").stream()
                    .filter(n -> n instanceof javafx.scene.control.Hyperlink h && "Şifremi Unuttum".equals(h.getText()))
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
        onFx(() -> {
            ListView<?> list = (ListView<?>) shell.lookup(".inbox-list");
            list.getSelectionModel().select(0);
            return null;
        });
        pause(500);
        onFx(() -> {
            shell.lookupAll(".button").stream().filter(n -> n instanceof Button b && "Belgeyi aç".equals(b.getText()))
                    .findFirst().ifPresent(n -> ((Button) n).fire());
            return null;
        });
        pause(2500);
        snap(stage, "04-belge-acik" + suffix);
        onFx(() -> {
            shell.lookupAll(".button").stream().filter(n -> n instanceof Button b && "Kod ile al".equals(b.getText()))
                    .findFirst().ifPresent(n -> ((Button) n).fire());
            return null;
        });
        pause(800);
        snap(stage, "04b-kod-ile-al" + suffix);
        onFx(() -> {
            com.desktop.ui.Dialogs.close();
            return null;
        });

        nav(shell, 1);
        pause(1500);
        snap(stage, "05-yeni-gonderi" + suffix);
        onFx(() -> {
            shell.lookupAll(".toggle-button").stream()
                    .filter(n -> n instanceof ToggleButton t && "Davet kodu ile".equals(t.getText()))
                    .findFirst().ifPresent(n -> ((ToggleButton) n).fire());
            return null;
        });
        pause(600);
        snap(stage, "05b-davet-kodu" + suffix);
        nav(shell, 3);
        pause(1200);
        snap(stage, "06-kisiler" + suffix);
        nav(shell, 4);
        pause(800);
        snap(stage, "07-anahtarim" + suffix);
        onFx(() -> {
            shell.lookupAll(".button").stream().filter(n -> n instanceof Button b && "Aç".equals(b.getText()))
                    .findFirst().ifPresent(n -> ((Button) n).fire());
            return null;
        });
        pause(1500);
        snap(stage, "07c-iki-adimli-kurulum" + suffix);
        onFx(() -> {
            com.desktop.ui.Dialogs.close();
            return null;
        });
        nav(shell, 5);
        pause(800);
        snap(stage, "07b-neden-guvenli" + suffix);

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
