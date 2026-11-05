package com.desktop.crypto;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;

/** Ed25519 imza: belgenin gönderen tarafından üretildiğini ve yolda değişmediğini kanıtlar. */
final class Signatures {

    private Signatures() {
    }

    static byte[] sign(PrivateKey key, byte[] data) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(key);
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Belge imzalanamadı", e);
        }
    }

    static boolean verify(PublicKey key, byte[] data, byte[] signatureBytes) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(key);
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (SignatureException e) {
            return false;
        } catch (GeneralSecurityException e) {
            throw new CryptoException("İmza doğrulanamadı", e);
        }
    }
}
