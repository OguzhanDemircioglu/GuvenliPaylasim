package com.desktop.service;

import com.desktop.crypto.CryptoException;
import com.desktop.crypto.Dpapi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Beni hatırla" kaydı: DPAPI ile şifreli, sunucuya ve süreye bağlı; bozuk ya da geçersiz kayıt silinir. */
@EnabledOnOs(OS.WINDOWS)
class RememberedLoginTest {

    private static final String SERVER = "https://guvenlipaylasim.up.railway.app";
    private static final byte[] ENTROPY = "test".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    @Test
    void dpapiRoundTripAndEntropyBinding() {
        byte[] secret = "özel anahtar paketi".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = Dpapi.protect(secret, ENTROPY);

        assertFalse(Arrays.equals(secret, sealed));
        assertFalse(new String(sealed, StandardCharsets.ISO_8859_1).contains("anahtar"));
        assertArrayEquals(secret, Dpapi.unprotect(sealed, ENTROPY));
        assertThrows(CryptoException.class, () -> Dpapi.unprotect(sealed, "başka".getBytes(StandardCharsets.UTF_8)));
        assertArrayEquals(new byte[0], Dpapi.unprotect(Dpapi.protect(new byte[0], ENTROPY), ENTROPY));
    }

    @Test
    void savedLoginIsEncryptedOnDiskAndLoadsBack() throws Exception {
        RememberedLogin store = new RememberedLogin(dir.resolve("remember.bin"));
        RememberedLogin.Data data = data(SERVER, Instant.now().plusSeconds(3600));
        store.save(data);

        String onDisk = Files.readString(dir.resolve("remember.bin"), StandardCharsets.ISO_8859_1);
        assertFalse(onDisk.contains("refresh-token-1"));
        assertFalse(onDisk.contains("+905551234567"));

        RememberedLogin fresh = new RememberedLogin(dir.resolve("remember.bin"));
        assertEquals(Optional.of(data), fresh.load(SERVER, Instant.now()));
    }

    @Test
    void rotatedTokenReplacesTheOldOne() {
        RememberedLogin store = new RememberedLogin(dir.resolve("remember.bin"));
        store.save(data(SERVER, Instant.now().plusSeconds(3600)));
        store.updateRefreshToken("refresh-token-2");

        assertEquals("refresh-token-2", new RememberedLogin(dir.resolve("remember.bin"))
                .load(SERVER, Instant.now()).orElseThrow().refreshToken());
    }

    @Test
    void expiredOtherServerOrCorruptLoginIsDeleted() throws Exception {
        Path file = dir.resolve("remember.bin");
        RememberedLogin store = new RememberedLogin(file);

        store.save(data(SERVER, Instant.now().minusSeconds(1)));
        assertTrue(store.load(SERVER, Instant.now()).isEmpty());
        assertFalse(Files.exists(file));

        store.save(data(SERVER, Instant.now().plusSeconds(3600)));
        assertTrue(store.load("http://localhost:8080", Instant.now()).isEmpty());
        assertFalse(Files.exists(file));

        Files.write(file, new byte[]{1, 2, 3});
        assertTrue(store.load(SERVER, Instant.now()).isEmpty());
        assertFalse(Files.exists(file));
    }

    @Test
    void clearDeletesTheFile() {
        RememberedLogin store = new RememberedLogin(dir.resolve("remember.bin"));
        store.save(data(SERVER, Instant.now().plusSeconds(3600)));
        assertTrue(store.exists());

        store.clear();

        assertFalse(store.exists());
        store.updateRefreshToken("after-clear");
        assertFalse(store.exists());
    }

    private static RememberedLogin.Data data(String server, Instant until) {
        return new RememberedLogin.Data(server, "+905551234567", "refresh-token-1", until.getEpochSecond(),
                "a2Vr", "bundle", "enc", "sig");
    }
}
