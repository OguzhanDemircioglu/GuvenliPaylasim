package com.desktop.update;

import atlantafx.base.theme.Styles;
import com.desktop.config.AppConfig;
import com.desktop.ui.Async;
import com.desktop.ui.Formats;
import com.desktop.ui.Ui;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Açılışta güncelleme kontrolü. Güncel ise hemen girişe geçer. Yeni sürüm süre içindeyse kullanıcı erteleyebilir;
 * süre dolduysa ya da sürüm zorunluysa güncellemeden devam edilemez.
 */
public final class UpdateGate extends StackPane {

    private final UpdateService updates = new UpdateService(AppConfig.get(), Clock.systemUTC());
    private final Runnable onContinue;
    private final VBox card = new VBox(16);

    public UpdateGate(Runnable onContinue) {
        this.onContinue = onContinue;
        getStyleClass().add("update-gate");
        card.getStyleClass().add("update-card");
        card.setMaxSize(520, VBox.USE_PREF_SIZE);
        card.setPrefWidth(520);
        StackPane.setAlignment(card, Pos.CENTER);

        if (!updates.enabled()) {
            Platform.runLater(onContinue);
            return;
        }
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(28, 28);
        card.getChildren().setAll(Ui.row(14, spinner, Ui.column(2, Ui.label("Güncellemeler Denetleniyor", Styles.TITLE_4),
                Ui.muted("Sürüm " + updates.current()))));
        getChildren().add(card);
        Async.run(updates::check, this::onCheck, e -> onContinue.run());
    }

    private void onCheck(UpdateService.Check check) {
        switch (check) {
            case UpdateService.Check.UpToDate up -> onContinue.run();
            case UpdateService.Check.Unavailable unavailable -> onContinue.run();
            case UpdateService.Check.OfflineBlocked blocked -> showOfflineBlocked(blocked.required());
            case UpdateService.Check.Available available -> Async.run(updates::installerBlocked,
                    blocked -> showAvailable(available.release(), blocked),
                    e -> showAvailable(available.release(), false));
        }
    }

    private void showAvailable(UpdateService.Release release, boolean installerBlocked) {
        boolean blocking = release.blocking(Instant.now());
        Label title = Ui.label(blocking ? "Güncelleme Gerekli" : "Yeni Sürüm Hazır", Styles.TITLE_2);
        Label subtitle = Ui.muted("Sürüm " + updates.current() + " → " + release.version());
        Label when = Ui.muted(blocking
                ? "Güvenliğiniz için bu sürüm zorunludur. Uygulama güncellenmeden açılmaz."
                : remaining(release.deadline()) + " sonra bu güncelleme zorunlu olacak.");

        TextArea notes = new TextArea(release.notes().isBlank() ? "Sürüm notu yok." : release.notes());
        notes.setEditable(false);
        notes.setWrapText(true);
        notes.setPrefRowCount(6);

        Button install = Ui.primary("Şimdi Güncelle", Material2OutlinedMZ.SYSTEM_UPDATE);
        install.setDefaultButton(true);
        install.setMaxWidth(Double.MAX_VALUE);
        install.setOnAction(e -> install(release));

        Label verified = new Label("Kurulum dosyası indirildikten sonra imzası ve özeti doğrulanır.",
                Ui.icon(Material2OutlinedMZ.VERIFIED_USER, 16));
        verified.getStyleClass().add(Styles.TEXT_MUTED);
        verified.setWrapText(true);

        card.getChildren().setAll(Ui.iconBadge(Material2OutlinedMZ.SYSTEM_UPDATE, blocking ? "warning" : "accent", 56),
                title, subtitle, when, notes, verified, install);
        if (installerBlocked) {
            // MSI başlatılırsa Windows reddeder ve uygulama boşuna kapanır; yerine kurulumsuz ZIP gösterilir.
            Label why = Ui.muted("Bu bilgisayarda Windows Akıllı Uygulama Denetimi açık ve kurulum dosyasını (MSI) "
                    + "engelliyor. Yeni sürümü kurulumsuz ZIP olarak indirin, bir klasöre çıkarın ve "
                    + "GuvenliPaylasim.exe'yi çalıştırın. Hatırlanan oturumunuz ve ayarlarınız korunur.");
            why.setWrapText(true);
            Button zip = Ui.primary("ZIP'i İndir", Material2OutlinedMZ.OPEN_IN_NEW);
            zip.setDefaultButton(true);
            zip.setMaxWidth(Double.MAX_VALUE);
            zip.setOnAction(e -> openInBrowser(release.portableUrl()));
            card.getChildren().setAll(Ui.iconBadge(Material2OutlinedMZ.SYSTEM_UPDATE, blocking ? "warning" : "accent",
                    56), title, subtitle, when, notes, why, zip);
        }
        if (!blocking) {
            Button later = Ui.button("Daha Sonra", null, Styles.FLAT);
            later.setMaxWidth(Double.MAX_VALUE);
            later.setOnAction(e -> onContinue.run());
            card.getChildren().add(later);
        }
    }

    private void install(UpdateService.Release release) {
        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        Label status = Ui.muted("İndiriliyor…");
        card.getChildren().setAll(Ui.iconBadge(Material2OutlinedMZ.SYSTEM_UPDATE, "accent", 56),
                Ui.label("Sürüm " + release.version() + " kuruluyor", Styles.TITLE_3), bar, status);
        Async.run(() -> {
            Path installer = updates.download(release, p -> Platform.runLater(() -> bar.setProgress(p)));
            Platform.runLater(() -> status.setText("İmza ve özet doğrulandı. Kurulum başlatılıyor…"));
            updates.launchInstaller(installer);
            return installer;
        }, installer -> Platform.exit(), failure -> {
            Label error = Ui.label(failure.getMessage(), "form-error");
            error.setWrapText(true);
            Button retry = Ui.primary("Tekrar Dene", Material2OutlinedMZ.REFRESH);
            retry.setOnAction(e -> install(release));
            card.getChildren().setAll(Ui.iconBadge(Material2OutlinedAL.ERROR_OUTLINE, "danger", 56),
                    Ui.label("Güncelleme Kurulamadı", Styles.TITLE_3), error, retry);
            if (!release.blocking(Instant.now())) {
                Button later = Ui.button("Daha Sonra", null, Styles.FLAT);
                later.setOnAction(e -> onContinue.run());
                card.getChildren().add(later);
            }
        });
    }

    /** Adres UpdateService.safeRepositoryUrl ile sadece bu deponun GitHub sayfası olabilir. */
    private static void openInBrowser(String url) {
        Thread.ofVirtual().start(() -> {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            } catch (Exception e) {
                Platform.runLater(() -> com.desktop.ui.Toasts.info("Tarayıcı açılamadı. Adres: " + url));
            }
        });
    }

    private void showOfflineBlocked(Version required) {
        Button retry = Ui.primary("Tekrar Dene", Material2OutlinedMZ.REFRESH);
        retry.setOnAction(e -> {
            getChildren().clear();
            getChildren().add(new UpdateGate(onContinue));
        });
        card.getChildren().setAll(Ui.iconBadge(Material2OutlinedAL.ERROR_OUTLINE, "warning", 56),
                Ui.label("Güncelleme Gerekli", Styles.TITLE_2),
                Ui.muted("Sürüm " + required + " zorunlu hale geldi, ancak güncelleme sunucusuna şu an "
                        + "ulaşılamıyor. Ağ bağlantınızı (proxy) kontrol edip tekrar deneyin."),
                retry);
    }

    private static String remaining(Instant deadline) {
        Duration left = Duration.between(Instant.now(), deadline);
        long days = left.toDays();
        if (days >= 1) {
            return days + " gün";
        }
        long hours = Math.max(1, left.toHours());
        return hours + " saat (" + Formats.fullDate(deadline) + ")";
    }
}
