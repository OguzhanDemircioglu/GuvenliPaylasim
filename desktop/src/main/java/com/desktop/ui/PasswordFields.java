package com.desktop.ui;

import atlantafx.base.controls.PasswordTextField;
import com.desktop.service.PasswordStrength;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;

import java.util.function.Function;

/**
 * Şifre alanları için ortak kurallar (docs/UI_Standards.md):
 * <ul>
 *   <li>En fazla {@link PasswordStrength#MAX_LENGTH} karakter yazılır; yapıştırılan fazlası kesilir.</li>
 *   <li>Yeni şifre alanının altında kural uyarısı (yazmaya başlayınca, kural tutana kadar).</li>
 *   <li>İlk şifre geçerliyse, tekrar alanına ilk karakter yazılınca eşleşene kadar "Şifreler uyuşmuyor."</li>
 * </ul>
 * Giriş ekranında uyarı gösterilmez; sadece düğme kapalı kalır.
 */
public final class PasswordFields {

    public static final String MISMATCH = "Şifreler uyuşmuyor.";

    private PasswordFields() {
    }

    /** Alana en fazla {@code max} karakter girilir. Maskeli alanın kendi biçimlendiricisi bozulmaz. */
    public static PasswordTextField limit(PasswordTextField field, int max) {
        field.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            String ch = event.getCharacter();
            boolean printable = !ch.isEmpty() && ch.charAt(0) >= ' ' && ch.charAt(0) != 127;
            if (printable && field.getSelection().getLength() == 0 && field.getPassword().length() >= max) {
                event.consume();
            }
        });
        // Yapıştırma: fazlası kesilir (değişiklik bittikten sonra, biçimlendiricinin üzerinden).
        field.passwordProperty().addListener((obs, old, value) -> {
            if (value.length() > max) {
                Platform.runLater(() -> {
                    if (field.getPassword().length() > max) {
                        field.deleteText(max, field.getLength());
                    }
                });
            }
        });
        return field;
    }

    public static PasswordTextField limit(PasswordTextField field) {
        return limit(field, PasswordStrength.MAX_LENGTH);
    }

    /** Alanın altında gösterilecek uyarı etiketi; boşken yer kaplamaz. */
    public static Label hint() {
        Label hint = new Label();
        hint.getStyleClass().add("field-hint");
        hint.setWrapText(true);
        hint.setVisible(false);
        hint.setManaged(false);
        return hint;
    }

    /**
     * Yeni şifre ve tekrarının uyarılarını bağlar. {@code check}: yeni şifre için kural mesajı (uygunsa null); kişisel
     * bilgi kontrolü gibi ekrana özgü kuralları içerebilir.
     */
    public static void bindHints(PasswordTextField next, PasswordTextField repeat, Label nextHint, Label repeatHint,
                                 Function<String, String> check) {
        Runnable update = () -> {
            String password = next.getPassword();
            String problem = password.isEmpty() ? null : check.apply(password);
            show(nextHint, problem);
            boolean mismatch = problem == null && !password.isEmpty() && !repeat.getPassword().isEmpty()
                    && !password.equals(repeat.getPassword());
            show(repeatHint, mismatch ? MISMATCH : null);
        };
        next.passwordProperty().addListener(obs -> update.run());
        repeat.passwordProperty().addListener(obs -> update.run());
    }

    public static void show(Label hint, String message) {
        hint.setText(message == null ? "" : message);
        hint.setVisible(message != null);
        hint.setManaged(message != null);
    }
}
