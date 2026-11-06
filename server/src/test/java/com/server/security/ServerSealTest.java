package com.server.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Security.md S2: veritabanındaki değer sunucu anahtarı olmadan açılamaz ve başka kolona taşınamaz. */
class ServerSealTest {

    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());
    private static final String BCRYPT = "{bcrypt}$2a$10$abcdefghijklmnopqrstuuJ7a1Q8a9OaTkx2Wl6XyZ0cDqvM5bK6";

    private final ServerSeal seal = new ServerSeal(KEY);

    @Test
    void roundTripAndTheStoredValueRevealsNothing() {
        String stored = seal.seal(BCRYPT, SealedColumns.PASSWORD_HASH);

        assertThat(stored).startsWith("s1:").doesNotContain("bcrypt").doesNotContain("$2a$");
        assertThat(seal.open(stored, SealedColumns.PASSWORD_HASH)).isEqualTo(BCRYPT);
        assertThat(seal.seal(BCRYPT, SealedColumns.PASSWORD_HASH)).isNotEqualTo(stored);
    }

    @Test
    void wrongKeyOrMovedToAnotherColumnCannotBeOpened() {
        String stored = seal.seal(BCRYPT, SealedColumns.PASSWORD_HASH);

        assertThatThrownBy(() -> new ServerSeal(OTHER_KEY).open(stored, SealedColumns.PASSWORD_HASH))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seal.open(stored, SealedColumns.PRIVATE_KEY_BUNDLE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tamperedValueCannotBeOpened() {
        String stored = seal.seal(BCRYPT, SealedColumns.PASSWORD_HASH);
        byte[] raw = Base64.getDecoder().decode(stored.substring(3));
        raw[raw.length - 1] ^= 1;

        assertThatThrownBy(() -> seal.open("s1:" + Base64.getEncoder().encodeToString(raw), SealedColumns.PASSWORD_HASH))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void legacyUnsealedValueIsReadAsIsUntilTheMigrationSealsIt() {
        assertThat(seal.open(BCRYPT, SealedColumns.PASSWORD_HASH)).isEqualTo(BCRYPT);
        assertThat(seal.open(null, SealedColumns.PASSWORD_HASH)).isNull();
    }

    @Test
    void keyMustBe256BitBase64() {
        assertThatThrownBy(() -> new ServerSeal(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ServerSeal("not base64!")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void largestAllowedKeyBundleStillFitsTheColumn() {
        String sealed = seal.seal("x".repeat(700), SealedColumns.PRIVATE_KEY_BUNDLE);
        assertThat(sealed.length()).isLessThanOrEqualTo(1000);
    }
}
