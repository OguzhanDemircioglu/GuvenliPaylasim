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

/** Ad soyad sadece harf, boşluk, kesme, nokta ve tire içerir; rakam ya da telefon numarası ad olarak kaydedilmez. */
class FullNamesValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final Validator validator = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @Test
    void lettersSpacesAndPunctuationAreAccepted() {
        assertThat(messages(new UpdateNameRequest("Ayşe Yılmaz"))).isEmpty();
        assertThat(messages(new UpdateNameRequest("  Ali Rıza O'Neil-Çelik Jr. "))).isEmpty();
        assertThat(new UpdateNameRequest("  Ayşe  ").fullName()).isEqualTo("Ayşe");
    }

    @Test
    void digitsAndSymbolsAreRejected() {
        assertThat(messages(new UpdateNameRequest("5551234567"))).containsExactly(UserMessages.FULL_NAME_INVALID);
        assertThat(messages(new UpdateNameRequest("Ayşe 2"))).containsExactly(UserMessages.FULL_NAME_INVALID);
        assertThat(messages(new UpdateNameRequest("<script>"))).containsExactly(UserMessages.FULL_NAME_INVALID);
        assertThat(messages(new UpdateNameRequest("-Ayşe"))).containsExactly(UserMessages.FULL_NAME_INVALID);
        assertThat(messages(new UpdateNameRequest("A".repeat(101)))).containsExactly(UserMessages.FULL_NAME_TOO_LONG);
    }

    @Test
    void registrationUsesTheSameRule() {
        RegisterRequest request = new RegisterRequest("5551234567", "123456", "Ali 1", "auth-secret-value",
                new KeyBundleRequest("enc", "sig", "bundle"));
        assertThat(messages(request)).contains(UserMessages.FULL_NAME_INVALID);
    }

    private Set<String> messages(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
    }
}
