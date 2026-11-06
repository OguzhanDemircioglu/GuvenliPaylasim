package com.desktop.ui.views;

import atlantafx.base.controls.PasswordTextField;
import com.desktop.service.AuthService;
import com.desktop.service.Session;
import com.desktop.ui.Async;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;

/**
 * Parola değiştirme formu. Özel anahtarlar yeni parolayla bu bilgisayarda yeniden şifrelenir ve parolayla
 * birlikte kaydedilir.
 */
final class PasswordForm extends VBox {

    private final PasswordTextField current = field("Mevcut parola");
    private final PasswordTextField next = field("En az 12 karakter, harf ve rakam");
    private final PasswordTextField repeat = field("Yeni parolayı tekrar girin");
    private final Label error = Ui.label("", "form-error");
    private final Button submit;

    PasswordForm(AuthService auth, Session session, String submitText, Runnable onChanged) {
        super(6);
        submit = Ui.primary(submitText, Material2OutlinedAL.LOCK);
        submit.setDefaultButton(true);
        submit.setMaxWidth(Double.MAX_VALUE);
        error.setWrapText(true);
        error.setManaged(false);

        submit.setOnAction(e -> {
            String invalid = AuthService.validateNewPassword(next.getPassword().toCharArray(),
                    repeat.getPassword().toCharArray());
            if (current.getPassword().isEmpty()) {
                invalid = "Mevcut parolanızı girin.";
            }
            showError(invalid);
            if (invalid != null) {
                return;
            }
            submit.setDisable(true);
            submit.setText("Anahtarlarınız yeni parolayla şifreleniyor…");
            Async.run(() -> {
                        auth.changePassword(session, current.getPassword().toCharArray(), next.getPassword().toCharArray());
                        return true;
                    },
                    ok -> {
                        submit.setDisable(false);
                        submit.setText(submitText);
                        current.clear();
                        next.clear();
                        repeat.clear();
                        onChanged.run();
                    },
                    failure -> {
                        submit.setDisable(false);
                        submit.setText(submitText);
                        showError(Toasts.describe(failure));
                    });
        });

        getChildren().addAll(
                Ui.label("Mevcut parola", "field-label"), current, gap(),
                Ui.label("Yeni parola", "field-label"), next, gap(),
                Ui.label("Yeni parola (tekrar)", "field-label"), repeat, gap(), error, submit);
        getStyleClass().add("login-form");
    }

    private void showError(String message) {
        error.setText(message == null ? "" : message);
        error.setManaged(message != null);
    }

    private static PasswordTextField field(String prompt) {
        PasswordTextField field = new PasswordTextField();
        field.setPromptText(prompt);
        return field;
    }

    private static VBox gap() {
        VBox gap = new VBox();
        gap.setMinHeight(8);
        return gap;
    }
}
