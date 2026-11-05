package com.desktop.ui.views;

import atlantafx.base.theme.Styles;
import com.desktop.service.AuthService;
import com.desktop.service.Session;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

/**
 * Yöneticinin verdiği ilk parola sistem kullanılmadan önce değiştirilir. Bu adımda kullanıcının anahtar çiftleri
 * bu bilgisayarda üretilir ve yeni parolayla şifrelenerek sunucuya gönderilir.
 */
public final class PasswordSetupView extends StackPane {

    public PasswordSetupView(AuthService auth, Session session, Runnable onDone, Runnable onLogout) {
        PasswordForm form = new PasswordForm(auth, session, "Parolamı belirle ve anahtarlarımı oluştur", () -> {
            Toasts.success("Parolanız değişti. Anahtarlarınız bu bilgisayarda oluşturuldu.");
            onDone.run();
        });

        Label info = Ui.muted("Yöneticinizin verdiği geçici parolayı kendi parolanızla değiştirin. Bu sırada "
                + "size özel şifreleme ve imza anahtarları bu bilgisayarda üretilecek. Parolanız sunucuya "
                + "gönderilmez; parolanızı unutursanız size gönderilmiş eski belgeler açılamaz.");
        info.setMaxWidth(420);

        Button logout = Ui.button("Çıkış yap", Material2OutlinedAL.EXIT_TO_APP, Styles.FLAT);
        logout.setOnAction(e -> onLogout.run());

        VBox card = Ui.surface(
                Ui.iconBadge(Material2OutlinedMZ.VPN_KEY, "accent", 56),
                Ui.label("Hoş geldiniz, " + session.user().fullName(), Styles.TITLE_2),
                info, form, logout);
        card.setMaxWidth(480);
        card.setMaxHeight(VBox.USE_PREF_SIZE);
        card.setPadding(new Insets(32));
        StackPane.setAlignment(card, Pos.CENTER);
        getChildren().add(card);
        getStyleClass().add("content-area");
    }
}
