package com.desktop.ui.views;

import atlantafx.base.controls.PasswordTextField;
import com.desktop.service.AuthService;
import com.desktop.service.PasswordStrength;
import com.desktop.service.Session;
import com.desktop.ui.Async;
import com.desktop.ui.Toasts;
import com.desktop.ui.Ui;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;

/**
 * Parola değiştirme formu. Özel anahtarlar yeni parolayla bu bilgisayarda yeniden şifrelenir ve parolayla
 * birlikte kaydedilir. Mevcut parola kurala uymadan ve yeni parola kurallardan geçmeden düğme kapalıdır; yeni parola
 * alanları dolu ama bir kural tutmuyorsa nedeni yazılır.
 */
final class PasswordForm extends VBox {

    private final PasswordTextField current = field("Mevcut parola");
    private final PasswordTextField next = field("12–20 karakter");
    private final PasswordTextField repeat = field("Yeni parolayı tekrar girin");
    private final Label error = Ui.label("", "form-error");
    private final Button submit;
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    PasswordForm(AuthService auth, Session session, String submitText, Runnable onChanged) {
        super(6);
        submit = Ui.primary(submitText, Material2OutlinedAL.LOCK);
        submit.setDefaultButton(true);
        submit.setMaxWidth(Double.MAX_VALUE);
        error.setWrapText(true);
        error.setManaged(false);

        BooleanBinding ready = Bindings.createBooleanBinding(
                () -> PasswordStrength.composition(current.getPassword()) == null && problem(session) == null,
                current.passwordProperty(), next.passwordProperty(), repeat.passwordProperty());
        submit.disableProperty().bind(busy.or(ready.not()));
        next.passwordProperty().addListener(obs -> showHint(session));
        repeat.passwordProperty().addListener(obs -> showHint(session));

        submit.setOnAction(e -> {
            String invalid = problem(session);
            showError(invalid);
            if (invalid != null) {
                return;
            }
            busy.set(true);
            submit.setText("Anahtarlarınız yeni parolayla şifreleniyor…");
            Async.run(() -> {
                        auth.changePassword(session, current.getPassword().toCharArray(), next.getPassword().toCharArray());
                        return true;
                    },
                    ok -> {
                        busy.set(false);
                        submit.setText(submitText);
                        current.clear();
                        next.clear();
                        repeat.clear();
                        showError(null);
                        onChanged.run();
                    },
                    failure -> {
                        busy.set(false);
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

    private String problem(Session session) {
        return AuthService.validateNewPassword(next.getPassword().toCharArray(), repeat.getPassword().toCharArray(),
                session.fullName(), session.user().phone());
    }

    /** Yeni parola ve tekrarı yazıldıysa ve bir kural tutmuyorsa nedeni gösterilir. */
    private void showHint(Session session) {
        boolean filled = !next.getPassword().isEmpty() && !repeat.getPassword().isEmpty();
        showError(filled ? problem(session) : null);
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
