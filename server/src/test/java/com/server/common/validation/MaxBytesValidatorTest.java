package com.server.common.validation;

import com.server.modules.auth.AuthMessages;
import com.server.modules.auth.dto.ChangePasswordRequest;
import com.server.modules.key.dto.KeyBundleRequest;
import com.server.modules.user.UserMessages;
import com.server.modules.auth.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * bcrypt 72 byte'tan uzun şifreyi hash'lerken exception fırlatır (500 olurdu); doğrulama bunu 400'e çevirmeli.
 */
class MaxBytesValidatorTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final Validator validator = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @Test
    void turkishCharactersCountAsTwoBytes() {
        String password = "ğ".repeat(37); // 37 karakter, 74 byte

        Set<ConstraintViolation<RegisterRequest>> violations =
                validator.validate(new RegisterRequest("5551234567", "123456", "Ali", password,
                        new KeyBundleRequest("enc", "sign", "bundle")));

        assertThat(violations).extracting(ConstraintViolation::getMessage).containsExactly(UserMessages.PASSWORD_TOO_LONG);
    }

    @Test
    void passwordAtTheLimitIsAccepted() {
        String password = "ğ".repeat(36); // 72 byte

        assertThat(validator.validate(new RegisterRequest("5551234567", "123456", "Ali", password,
                new KeyBundleRequest("enc", "sign", "bundle")))).isEmpty();
    }

    @Test
    void newPasswordIsCheckedToo() {
        Set<ConstraintViolation<ChangePasswordRequest>> violations =
                validator.validate(new ChangePasswordRequest("current-password", "ş".repeat(40),
                        new KeyBundleRequest("enc", "sign", "bundle")));

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly(AuthMessages.NEW_PASSWORD_TOO_LONG);
    }
}
