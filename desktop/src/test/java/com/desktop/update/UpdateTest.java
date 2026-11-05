package com.desktop.update;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateTest {

    private static final String SHA = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @Test
    void versionsCompareNumerically() {
        assertTrue(Version.parse("v1.10.0").isNewerThan(Version.parse("1.9.9")));
        assertTrue(Version.parse("2.0").isNewerThan(Version.parse("1.99.99")));
        assertFalse(Version.parse("1.0.0").isNewerThan(Version.parse("1.0.0")));
    }

    @Test
    void signatureBindsVersionDigestAndMandatoryFlag() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String signature = ReleaseSignature.sign(keys.getPrivate(), "1.1.0", SHA, false);

        assertTrue(ReleaseSignature.verify(keys.getPublic(), "v1.1.0", SHA.toUpperCase(), false, signature));
        assertFalse(ReleaseSignature.verify(keys.getPublic(), "1.2.0", SHA, false, signature), "sürüm değiştirilemez");
        assertFalse(ReleaseSignature.verify(keys.getPublic(), "1.1.0", SHA.replace('9', '8'), false, signature),
                "başka bir dosyanın özeti kabul edilmez");
        assertFalse(ReleaseSignature.verify(keys.getPublic(), "1.1.0", SHA, true, signature),
                "zorunluluk bilgisi değiştirilemez");

        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        assertFalse(ReleaseSignature.verify(other.getPublic(), "1.1.0", SHA, false, signature),
                "başka bir anahtarla imzalanmış sürüm kabul edilmez");
    }

    @Test
    void embeddedPublicKeyIsValid() throws Exception {
        try (var in = UpdateTest.class.getResourceAsStream("/com/desktop/update-public-key.txt")) {
            ReleaseSignature.publicKey(new String(in.readAllBytes()));
        }
    }

    @Test
    void releaseBlocksAfterGracePeriodOrWhenMandatory() {
        Instant deadline = Instant.parse("2026-11-08T00:00:00Z");
        UpdateService.Release release = new UpdateService.Release(Version.parse("1.1.0"), "", deadline, false,
                "x.msi", "u", SHA, Base64.getEncoder().encodeToString(new byte[64]));
        assertFalse(release.blocking(deadline.minusSeconds(1)));
        assertTrue(release.blocking(deadline));

        UpdateService.Release mandatory = new UpdateService.Release(Version.parse("1.1.0"), "", deadline, true,
                "x.msi", "u", SHA, "");
        assertTrue(mandatory.blocking(deadline.minusSeconds(86_400)));
    }
}
