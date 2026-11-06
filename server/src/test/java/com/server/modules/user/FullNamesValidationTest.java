package com.server.modules.user;

import com.server.modules.auth.dto.RegisterRequest;
import com.server.modules.key.dto.KeyBundleRequest;
import com.server.modules.user.dto.UpdateNameRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ad soyad: 2–3 kelime; sadece harf, boşluk, kesme, nokta ve tire. Rakam ya da telefon numarası ad olarak
 * kaydedilmez.
 */
class FullNamesValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final Validator validator = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @Test
    void twoOrThreeWordsAreAccepted() {
        assertThat(messages(new UpdateNameRequest("Ayşe Yılmaz"))).isEmpty();
        assertThat(messages(new UpdateNameRequest("  Ali   Rıza  O'Neil-Çelik "))).isEmpty();
        assertThat(new UpdateNameRequest("  Ali   Rıza  Çelik ").fullName()).isEqualTo("Ali Rıza Çelik");
        assertThat(messages(new UpdateNameRequest("İsmail Işık"))).isEmpty();
    }

    @Test
    void oneOrMoreThanThreeWordsAreRejected() {
        assertThat(messages(new UpdateNameRequest("Ayşe"))).containsExactly(UserMessages.FULL_NAME_WORDS);
        assertThat(messages(new UpdateNameRequest("Ali Rıza Can Çelik"))).containsExactly(UserMessages.FULL_NAME_WORDS);
    }

    @Test
    void digitsAndSymbolsAreRejected() {
        assertThat(messages(new UpdateNameRequest("5551234567"))).contains(UserMessages.FULL_NAME_INVALID);
        assertThat(messages(new UpdateNameRequest("Ayşe 2"))).contains(UserMessages.FULL_NAME_INVALID);
        assertThat(messages(new UpdateNameRequest("<script> x"))).contains(UserMessages.FULL_NAME_INVALID);
        assertThat(messages(new UpdateNameRequest("-Ayşe Kaya"))).contains(UserMessages.FULL_NAME_INVALID);
        assertThat(messages(new UpdateNameRequest("Ayşe -Kaya"))).containsExactly(UserMessages.FULL_NAME_WORDS);
        assertThat(messages(new UpdateNameRequest("A".repeat(50) + " " + "B".repeat(50))))
                .contains(UserMessages.FULL_NAME_TOO_LONG);
    }

    @Test
    void registrationUsesTheSameRule() {
        KeyBundleRequest keys = new KeyBundleRequest("enc", "sig", "bundle");
        assertThat(messages(new RegisterRequest("5551234567", "123456", "Ali 1", "auth-secret-value", keys)))
                .contains(UserMessages.FULL_NAME_INVALID);
        assertThat(messages(new RegisterRequest("5551234567", "123456", "Ali", "auth-secret-value", keys)))
                .containsExactly(UserMessages.FULL_NAME_WORDS);
        assertThat(messages(new RegisterRequest("5551234567", "123456", "Ali Veli", "auth-secret-value", keys)))
                .isEmpty();
    }

    private Set<String> messages(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
    }
}
