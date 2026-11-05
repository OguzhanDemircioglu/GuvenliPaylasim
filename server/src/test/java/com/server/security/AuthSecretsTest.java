package com.server.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSecretsTest {

    /**
     * Masaüstü istemcideki PasswordKeys ile aynı değer (desktop: CryptoTest). Biri değişirse ilk admin ve demo
     * kullanıcıları giriş yapamaz; bu test iki tarafın uyumunu korur.
     */
    @Test
    void matchesDesktopDerivation() {
        assertThat(AuthSecrets.derive("  Admin@Test.LOCAL ", "Admin12345"))
                .isEqualTo("n3yyVX4DZreKguvSq6klsfXDt_VTmuONob1pTJijKSM");
    }
}
