package com.server.modules.totp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RFC 6238 ek B (SHA-1) ve RFC 4648 test değerleri. */
class TotpTest {

    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void rfc6238Sha1Vectors() {
        // RFC'deki 8 haneli değerlerin son 6 hanesi.
        assertThat(Totp.code(RFC_SECRET, 59L / 30)).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, 1111111111L / 30)).isEqualTo("050471");
        assertThat(Totp.code(RFC_SECRET, 1234567890L / 30)).isEqualTo("005924");
        assertThat(Totp.code(RFC_SECRET, 2000000000L / 30)).isEqualTo("279037");
        assertThat(Totp.code(RFC_SECRET, 20000000000L / 30)).isEqualTo("353130");
    }

    @Test
    void rfc4648Base32() {
        assertThat(Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(new String(Totp.fromBase32("MZXW6YTBOI"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
        byte[] secret = Totp.newSecret();
        assertThat(Totp.base32(secret)).hasSize(32);
        assertThat(Totp.fromBase32(Totp.base32(secret))).isEqualTo(secret);
        assertThatThrownBy(() -> Totp.fromBase32("abc1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oneStepClockDriftIsAcceptedAndACodeCannotBeReused() {
        long now = 1_000_000;
        String previous = Totp.code(RFC_SECRET, now - 1);
        String next = Totp.code(RFC_SECRET, now + 1);
        String old = Totp.code(RFC_SECRET, now - 2);

        assertThat(Totp.matchingStep(RFC_SECRET, previous, now, -1)).isEqualTo(now - 1);
        assertThat(Totp.matchingStep(RFC_SECRET, next, now, -1)).isEqualTo(now + 1);
        assertThat(Totp.matchingStep(RFC_SECRET, old, now, -1)).isEqualTo(-1);
        // Son kullanılan adım ve öncesi tekrar kabul edilmez.
        assertThat(Totp.matchingStep(RFC_SECRET, previous, now, now - 1)).isEqualTo(-1);
    }

    @Test
    void uriIsWhatAuthenticatorAppsExpect() {
        assertThat(Totp.uri("Lockpost", "+905551234567", "JBSWY3DPEHPK3PXP")).isEqualTo(
                "otpauth://totp/Lockpost:%2B905551234567?secret=JBSWY3DPEHPK3PXP"
                        + "&issuer=Lockpost&algorithm=SHA1&digits=6&period=30");
    }
}
