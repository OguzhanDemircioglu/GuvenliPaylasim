package com.server.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSecretsTest {

    /**
     * v2 türetmesi (tuz = "guvenli-paylasim/v2|" + E.164 telefon, Crypto_Design §7.3). Beklenen değer bağımsız bir
     * PBKDF2/HMAC uygulamasıyla (Python hashlib) hesaplandı. Masaüstü istemcideki PasswordKeys de aynı değeri
     * üretmelidir (desktop: CryptoTest); biri değişirse demo kullanıcıları giriş yapamaz.
     */
    @Test
    void matchesV2Derivation() {
        assertThat(AuthSecrets.derive("+905551234567", "DemoParola2026!"))
                .isEqualTo("MW9rjw23gWedr5y5msOuC7DRjgHO7PHtMjTskHEwKus");
    }
}
