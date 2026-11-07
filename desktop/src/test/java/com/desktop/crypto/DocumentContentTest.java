package com.desktop.crypto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentContentTest {

    @Test
    void normalDocumentsKeepTheOldFormatSoOlderVersionsStillOpenThem() {
        byte[] bytes = DocumentContent.ofFile("a.pdf", "application/pdf", new byte[]{1, 2}).serialize();
        assertEquals(1, bytes[0]);
        DocumentContent back = DocumentContent.deserialize(bytes);
        assertFalse(back.viewOnly());
        assertEquals("a.pdf", back.fileName());
        assertArrayEquals(new byte[]{1, 2}, back.data());
    }

    @Test
    void viewOnlyUsesTheNewFormatAndSurvivesSealing() {
        DocumentContent content = DocumentContent.ofText("sadece bak").asViewOnly();
        assertEquals(2, content.serialize()[0], "eski sürümler v2'yi tanımaz, açmayı reddeder");

        UserKeys sender = UserKeys.generate();
        UserKeys alice = UserKeys.generate();
        DocumentCrypto.Sealed sealed = DocumentCrypto.seal(content, sender,
                List.of(new DocumentCrypto.Recipient(1, alice.encryptionPublicKey())));
        DocumentContent opened = DocumentCrypto.open(sealed.payload(), sealed.signature(),
                sealed.wrappedKeys().get(1L), sender.signingPublicKey(), alice).content();
        assertTrue(opened.viewOnly());
        assertEquals("sadece bak", opened.asText());
    }

    @Test
    void unknownVersionIsRejected() {
        byte[] bytes = DocumentContent.ofText("x").serialize();
        bytes[0] = 3;
        assertThrows(CryptoException.class, () -> DocumentContent.deserialize(bytes));
    }
}
