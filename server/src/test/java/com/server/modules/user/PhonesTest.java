package com.server.modules.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Crypto_Design §7.2: 10 hane, 5 ile başlar; başında 0 ya da +90 yoktur. */
class PhonesTest {

    @Test
    void tenDigitMobileNumberBecomesE164() {
        assertThat(Phones.toE164("5551234567")).isEqualTo("+905551234567");
        assertThat(Phones.toE164(" 555 123 45 67 ")).isEqualTo("+905551234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"05551234567", "+905551234567", "905551234567", "4551234567", "555123456", "55512345678",
            "555123456a", ""})
    void otherFormatsAreRejected(String input) {
        assertThatThrownBy(() -> Phones.toE164(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullIsRejected() {
        assertThatThrownBy(() -> Phones.toE164(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
