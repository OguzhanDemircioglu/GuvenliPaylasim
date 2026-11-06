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
                "x.msi", "u", SHA, Base64.getEncoder().encodeToString(new byte[64]), "z");
        assertFalse(release.blocking(deadline.minusSeconds(1)));
        assertTrue(release.blocking(deadline));

        UpdateService.Release mandatory = new UpdateService.Release(Version.parse("1.1.0"), "", deadline, true,
                "x.msi", "u", SHA, "", "z");
        assertTrue(mandatory.blocking(deadline.minusSeconds(86_400)));
    }

    @Test
    void smartAppControlStateIsReadFromRegistryOutput() {
        String on = "\r\nHKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Control\\CI\\Policy\r\n"
                + "    VerifiedAndReputablePolicyState    REG_DWORD    0x1\r\n";
        assertTrue(UpdateService.smartAppControlEnforced(on));
        assertFalse(UpdateService.smartAppControlEnforced(on.replace("0x1", "0x0")), "kapalı");
        assertFalse(UpdateService.smartAppControlEnforced(on.replace("0x1", "0x2")), "değerlendirme modu engellemez");
        assertFalse(UpdateService.smartAppControlEnforced("ERROR: The system was unable to find the specified"));
    }

    @Test
    void onlyThisRepositoryIsOpenedInTheBrowser() {
        String repo = "OguzhanDemircioglu/GuvenliPaylasim";
        String zip = "https://github.com/" + repo + "/releases/download/v2.0.2/GuvenliPaylasim-2.0.2-kurulumsuz.zip";
        assertTrue(UpdateService.safeRepositoryUrl(zip, repo).equals(zip));
        String fallback = "https://github.com/" + repo + "/releases/latest";
        assertTrue(UpdateService.safeRepositoryUrl("https://evil.example/x.zip", repo).equals(fallback));
        assertTrue(UpdateService.safeRepositoryUrl("https://github.com/attacker/x/releases", repo).equals(fallback));
        assertTrue(UpdateService.safeRepositoryUrl("https://github.com/" + repo + "/../../a/b", repo).equals(fallback));
        assertTrue(UpdateService.safeRepositoryUrl(null, repo).equals(fallback));
    }

    @Test
    void oldDownloadsAreDeletedButRecentOnesKept(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp)
            throws Exception {
        java.nio.file.Path old = java.nio.file.Files.createDirectory(temp.resolve("gps-update111"));
        java.nio.file.Files.writeString(old.resolve("GuvenliPaylasim-2.0.1.msi"), "x");
        java.nio.file.Path recent = java.nio.file.Files.createDirectory(temp.resolve("gps-update222"));
        java.nio.file.Path other = java.nio.file.Files.createDirectory(temp.resolve("baska-klasor"));
        Instant now = Instant.now();
        java.nio.file.Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.from(now.minusSeconds(7200)));
        java.nio.file.Files.setLastModifiedTime(other, java.nio.file.attribute.FileTime.from(now.minusSeconds(7200)));

        UpdateService.deleteOldDownloads(temp, now);

        assertFalse(java.nio.file.Files.exists(old));
        assertTrue(java.nio.file.Files.exists(recent));
        assertTrue(java.nio.file.Files.exists(other), "başka klasörlere dokunulmaz");
    }
}
