package com.desktop.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoTest {

    /**
     * v2 (tuz = telefon). Sunucudaki AuthSecrets ile aynı değer (server: AuthSecretsTest); değer bağımsız bir
     * uygulamayla (Python hashlib) da hesaplandı. Biri değişirse demo kullanıcıları giriş yapamaz.
     */
    @Test
    void authSecretMatchesServerDerivation() {
        PasswordKeys.Derived derived = PasswordKeys.derive("+905551234567", "DemoParola2026!".toCharArray());
        assertEquals(AUTH_SECRET_VECTOR, derived.authSecret());
        assertEquals(32, derived.kek().length);
        assertNotEquals(derived.authSecret(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(derived.kek()), "kek authSecret'tan farklı olmalı");
    }

    static final String AUTH_SECRET_VECTOR = "MW9rjw23gWedr5y5msOuC7DRjgHO7PHtMjTskHEwKus";

    @Test
    void privateKeysOpenOnlyWithTheRightPasswordAndPhone() {
        UserKeys keys = UserKeys.generate();
        byte[] kek = PasswordKeys.derive("+905550000001", "Doğru-Parola1".toCharArray()).kek();
        String bundle = keys.seal(kek, "+905550000001");

        UserKeys opened = UserKeys.open(bundle, kek, "+905550000001", keys.encryptionPublicKey(), keys.signingPublicKey());
        assertEquals(keys.fingerprint(), opened.fingerprint());

        byte[] wrongKek = PasswordKeys.derive("+905550000001", "Yanlış-Parola1".toCharArray()).kek();
        assertThrows(CryptoException.class,
                () -> UserKeys.open(bundle, wrongKek, "+905550000001", keys.encryptionPublicKey(), keys.signingPublicKey()));
        assertThrows(CryptoException.class,
                () -> UserKeys.open(bundle, kek, "+905550000002", keys.encryptionPublicKey(), keys.signingPublicKey()));

        UserKeys other = UserKeys.generate();
        assertThrows(CryptoException.class,
                () -> UserKeys.open(bundle, kek, "+905550000001", other.encryptionPublicKey(), keys.signingPublicKey()),
                "paket başka bir anahtar çiftine ait gösterilirse kabul edilmemeli");
    }

    @Test
    void everyRecipientOpensTheSameDocumentAndOutsidersCannot() {
        UserKeys sender = UserKeys.generate();
        UserKeys alice = UserKeys.generate();
        UserKeys bob = UserKeys.generate();
        UserKeys eve = UserKeys.generate();

        DocumentContent content = DocumentContent.ofFile("Gizli Rapor.pdf", "application/pdf", "%PDF-1.7 içerik".getBytes());
        DocumentCrypto.Sealed sealed = DocumentCrypto.seal(content, sender, List.of(
                new DocumentCrypto.Recipient(1, alice.encryptionPublicKey()),
                new DocumentCrypto.Recipient(2, bob.encryptionPublicKey())));

        for (var entry : List.of(new Object[]{1L, alice}, new Object[]{2L, bob})) {
            DocumentCrypto.Opened opened = DocumentCrypto.open(sealed.payload(), sealed.signature(),
                    sealed.wrappedKeys().get((Long) entry[0]), sender.signingPublicKey(), (UserKeys) entry[1]);
            assertEquals(DocumentCrypto.SignatureStatus.VERIFIED, opened.signature());
            assertEquals("Gizli Rapor.pdf", opened.content().fileName());
            assertArrayEquals(content.data(), opened.content().data());
        }

        assertThrows(CryptoException.class, () -> DocumentCrypto.open(sealed.payload(), sealed.signature(),
                sealed.wrappedKeys().get(1L), sender.signingPublicKey(), eve), "başkasının anahtarıyla açılmamalı");
    }

    @Test
    void tamperedPayloadOrForgedSenderIsRejectedBeforeDecryption() {
        UserKeys sender = UserKeys.generate();
        UserKeys alice = UserKeys.generate();
        UserKeys impostor = UserKeys.generate();
        DocumentCrypto.Sealed sealed = DocumentCrypto.seal(DocumentContent.ofText("Toplantı saat 14:00"), sender,
                List.of(new DocumentCrypto.Recipient(1, alice.encryptionPublicKey())));
        String wrapped = sealed.wrappedKeys().get(1L);

        byte[] tampered = sealed.payload().clone();
        tampered[tampered.length - 1] ^= 1;
        assertThrows(CryptoException.class,
                () -> DocumentCrypto.open(tampered, sealed.signature(), wrapped, sender.signingPublicKey(), alice));

        assertThrows(CryptoException.class,
                () -> DocumentCrypto.open(sealed.payload(), sealed.signature(), wrapped, impostor.signingPublicKey(), alice),
                "imza başka birinin anahtarıyla doğrulanmamalı");
    }

    @Test
    void wrappedKeyIsBoundToItsDocument() {
        UserKeys sender = UserKeys.generate();
        UserKeys alice = UserKeys.generate();
        List<DocumentCrypto.Recipient> to = List.of(new DocumentCrypto.Recipient(1, alice.encryptionPublicKey()));
        DocumentCrypto.Sealed first = DocumentCrypto.seal(DocumentContent.ofText("birinci"), sender, to);
        DocumentCrypto.Sealed second = DocumentCrypto.seal(DocumentContent.ofText("ikinci"), sender, to);

        assertThrows(CryptoException.class, () -> DocumentCrypto.open(second.payload(), second.signature(),
                first.wrappedKeys().get(1L), sender.signingPublicKey(), alice));
    }

    @Test
    void unknownSenderKeyStillOpensButSaysSo() {
        UserKeys sender = UserKeys.generate();
        UserKeys alice = UserKeys.generate();
        DocumentCrypto.Sealed sealed = DocumentCrypto.seal(DocumentContent.ofText("Merhaba"), sender,
                List.of(new DocumentCrypto.Recipient(1, alice.encryptionPublicKey())));

        DocumentCrypto.Opened opened = DocumentCrypto.open(sealed.payload(), sealed.signature(),
                sealed.wrappedKeys().get(1L), null, alice);
        assertEquals(DocumentCrypto.SignatureStatus.UNKNOWN, opened.signature());
        assertEquals("Merhaba", opened.content().asText());
    }

    @Test
    void fingerprintIsGroupedHex() {
        String fingerprint = UserKeys.generate().fingerprint();
        assertTrue(fingerprint.matches("([0-9A-F]{4} ){7}[0-9A-F]{4}"), fingerprint);
    }
}
