package com.desktop.crypto;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Crypto_Design §7.6 ve Security.md S3: kodla açılır, yanlış kodla açılmaz; sunucu iki yönde de araya sahte anahtar
 * koyarsa iki taraf da bunu fark eder.
 */
class InviteCryptoTest {

    private static final String ALI_PHONE = "+905550000001";
    private static final String AYSE_PHONE = "+905550000002";

    private final UserKeys ali = UserKeys.generate();
    private final UserKeys ayse = UserKeys.generate();
    private final UserKeys mallory = UserKeys.generate();

    @Test
    void codeIs16CrockfordCharactersAndForgivingToType() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = InviteCrypto.generateCode();
            assertEquals(16, code.length());
            assertTrue(code.matches("[0-9A-HJKMNP-TV-Z]{16}"), code);
            assertTrue(seen.add(code));
        }
        assertEquals("K7M2Q9XA4TPBW8RC", InviteCrypto.normalize("k7m2-q9xa 4tpb-w8rc"));
        assertEquals("11000000000000AB", InviteCrypto.normalize("ILOO-0000-0000-00AB"));
        assertEquals("K7M2-Q9XA-4TPB-W8RC", InviteCrypto.format("K7M2Q9XA4TPBW8RC"));
        assertNull(InviteCrypto.normalize("K7M2-Q9XA-4TPB"));
        assertNull(InviteCrypto.normalize("K7M2-Q9XA-4TPB-W8RU"));
    }

    @Test
    void bothSidesVerifyEachOtherAndTheRecipientCanOpenTheDocument() {
        InviteCrypto.Created created = InviteCrypto.create(DocumentContent.ofText("İlk kutu"), ali);
        String code = InviteCrypto.normalize(InviteCrypto.format(created.code()));
        assertEquals(created.verifierHash(), hex(DocumentCrypto.sha256(
                java.util.Base64.getDecoder().decode(InviteCrypto.verifier(code)))));

        InviteCrypto.Accepted accepted = InviteCrypto.accept(code, created.payload(), created.signature(),
                created.wrapped(), ali.encryptionPublicKey(), ali.signingPublicKey(), ALI_PHONE, ayse, AYSE_PHONE);
        assertEquals(ali.fingerprint(), accepted.senderFingerprint());
        assertTrue(InviteCrypto.contactSignatureValid(ayse, AYSE_PHONE, ALI_PHONE, ali.fingerprint(),
                accepted.contactSignature()));

        DocumentCrypto.Opened opened = DocumentCrypto.open(created.payload(), created.signature(),
                accepted.wrappedKey(), ali.signingPublicKey(), ayse);
        assertEquals("İlk kutu", opened.content().asText());

        String aliSignature = InviteCrypto.verifyAcceptance(created.selfWrapped(), created.verifierHash(),
                accepted.seal(), ayse.encryptionPublicKey(), ayse.signingPublicKey(), AYSE_PHONE, ali, ALI_PHONE);
        assertTrue(InviteCrypto.contactSignatureValid(ali, ALI_PHONE, AYSE_PHONE, ayse.fingerprint(), aliSignature));
    }

    @Test
    void wrongCodeDoesNotOpen() {
        InviteCrypto.Created created = InviteCrypto.create(DocumentContent.ofText("x"), ali);
        String other = InviteCrypto.generateCode();
        assertNotEquals(created.code(), other);

        assertThrows(CryptoException.class, () -> InviteCrypto.accept(other, created.payload(), created.signature(),
                created.wrapped(), ali.encryptionPublicKey(), ali.signingPublicKey(), ALI_PHONE, ayse, AYSE_PHONE));
    }

    @Test
    void serverSubstitutingTheSendersKeyIsDetectedByTheRecipient() {
        InviteCrypto.Created created = InviteCrypto.create(DocumentContent.ofText("x"), ali);

        CryptoException error = assertThrows(CryptoException.class, () -> InviteCrypto.accept(created.code(),
                created.payload(), created.signature(), created.wrapped(), mallory.encryptionPublicKey(),
                mallory.signingPublicKey(), ALI_PHONE, ayse, AYSE_PHONE));
        assertTrue(error.getMessage().contains("sahte"), error.getMessage());
    }

    @Test
    void serverSubstitutingTheRecipientsKeyIsDetectedByTheSender() {
        InviteCrypto.Created created = InviteCrypto.create(DocumentContent.ofText("x"), ali);
        InviteCrypto.Accepted accepted = InviteCrypto.accept(created.code(), created.payload(), created.signature(),
                created.wrapped(), ali.encryptionPublicKey(), ali.signingPublicKey(), ALI_PHONE, ayse, AYSE_PHONE);

        // Sunucu Ali'ye Ayşe yerine Mallory'nin anahtarını gösterirse mühür tutmaz.
        assertThrows(CryptoException.class, () -> InviteCrypto.verifyAcceptance(created.selfWrapped(),
                created.verifierHash(), accepted.seal(), mallory.encryptionPublicKey(), mallory.signingPublicKey(),
                AYSE_PHONE, ali, ALI_PHONE));

        // Kodu bilmeyen sunucu Mallory adına mühür de üretemez (rastgele mühür).
        assertThrows(CryptoException.class, () -> InviteCrypto.verifyAcceptance(created.selfWrapped(),
                created.verifierHash(), java.util.Base64.getEncoder().encodeToString(new byte[32]),
                mallory.encryptionPublicKey(), mallory.signingPublicKey(), AYSE_PHONE, ali, ALI_PHONE));
    }

    @Test
    void tamperedContactRecordIsNotAccepted() {
        String signature = InviteCrypto.signContact(ali, ALI_PHONE, AYSE_PHONE, ayse.fingerprint());

        assertTrue(InviteCrypto.contactSignatureValid(ali, ALI_PHONE, AYSE_PHONE, ayse.fingerprint(), signature));
        assertFalse(InviteCrypto.contactSignatureValid(ali, ALI_PHONE, AYSE_PHONE, mallory.fingerprint(), signature));
        assertFalse(InviteCrypto.contactSignatureValid(ali, ALI_PHONE, "+905559999999", ayse.fingerprint(), signature));
        assertFalse(InviteCrypto.contactSignatureValid(ayse, ALI_PHONE, AYSE_PHONE, ayse.fingerprint(), signature));
        assertFalse(InviteCrypto.contactSignatureValid(ali, ALI_PHONE, AYSE_PHONE, ayse.fingerprint(), null));
    }

    @Test
    void tamperedPayloadIsRejectedEvenWithTheRightCode() {
        InviteCrypto.Created created = InviteCrypto.create(DocumentContent.ofText("x"), ali);
        byte[] tampered = created.payload().clone();
        tampered[tampered.length - 1] ^= 1;

        assertThrows(CryptoException.class, () -> InviteCrypto.accept(created.code(), tampered, created.signature(),
                created.wrapped(), ali.encryptionPublicKey(), ali.signingPublicKey(), ALI_PHONE, ayse, AYSE_PHONE));
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
