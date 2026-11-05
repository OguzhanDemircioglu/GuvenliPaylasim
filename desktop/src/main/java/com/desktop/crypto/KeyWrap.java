package com.desktop.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.KDF;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.HKDFParameterSpec;
import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * Belge anahtarını tek bir alıcının açık anahtarına sarmalar (ECIES benzeri, docs/Crypto_Design.md §4.2):
 * <ol>
 *   <li>Her sarmalama için yeni, geçici bir X25519 anahtar çifti üretilir.</li>
 *   <li>Geçici özel anahtar ile alıcının açık anahtarından ortak sır hesaplanır (X25519).</li>
 *   <li>Ortak sırdan HKDF-SHA256 ile 256 bitlik bir sarmalama anahtarı türetilir.</li>
 *   <li>Belge anahtarı bu anahtarla AES-256-GCM ile şifrelenir.</li>
 * </ol>
 * Biçim: {@code sürüm(1) | geçici açık anahtar uzunluğu(1) | geçici açık anahtar (X.509) | AES-GCM çıktısı}.
 * Geçici özel anahtar hemen atılır; aynı ortak sırrı sadece alıcının özel anahtarı yeniden hesaplayabilir.
 */
final class KeyWrap {

    private static final byte VERSION = 1;
    private static final byte[] INFO = Aead.utf8("gps-wrap/v1");

    private KeyWrap() {
    }

    /** @param binding sarmalanan anahtarın hangi içeriğe ait olduğunu bağlar (AAD); başka belgeye taşınamaz. */
    static byte[] wrap(byte[] documentKey, PublicKey recipient, byte[] binding) {
        try {
            KeyPair ephemeral = KeyPairGenerator.getInstance("X25519").generateKeyPair();
            byte[] ephemeralPublic = ephemeral.getPublic().getEncoded();
            byte[] wrapKey = deriveWrapKey(ephemeral.getPrivate(), recipient, ephemeralPublic, recipient.getEncoded());
            byte[] sealed;
            try {
                sealed = Aead.encrypt(wrapKey, documentKey, binding);
            } finally {
                Arrays.fill(wrapKey, (byte) 0);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(VERSION);
            out.write(ephemeralPublic.length);
            out.writeBytes(ephemeralPublic);
            out.writeBytes(sealed);
            return out.toByteArray();
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Belge anahtarı sarmalanamadı", e);
        }
    }

    static byte[] unwrap(byte[] wrapped, KeyPair recipient, byte[] binding) {
        if (wrapped.length < 3 || wrapped[0] != VERSION) {
            throw new CryptoException("Sarmalanmış anahtarın biçimi tanınmıyor");
        }
        int length = wrapped[1] & 0xFF;
        byte[] ephemeralPublic = Arrays.copyOfRange(wrapped, 2, 2 + length);
        byte[] sealed = Arrays.copyOfRange(wrapped, 2 + length, wrapped.length);
        try {
            PublicKey ephemeral = KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(ephemeralPublic));
            byte[] wrapKey = deriveWrapKey(recipient.getPrivate(), ephemeral, ephemeralPublic,
                    recipient.getPublic().getEncoded());
            try {
                return Aead.decrypt(wrapKey, sealed, binding);
            } finally {
                Arrays.fill(wrapKey, (byte) 0);
            }
        } catch (AEADBadTagException e) {
            throw new CryptoException("Bu belge sizin anahtarınızla açılamıyor ya da değiştirilmiş");
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Belge anahtarı açılamadı", e);
        }
    }

    /** HKDF tuzu iki açık anahtardır; böylece türetilen anahtar bu iki tarafa özgü olur. */
    private static byte[] deriveWrapKey(PrivateKey own, PublicKey other, byte[] ephemeralPublic, byte[] recipientPublic)
            throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("X25519");
        agreement.init(own);
        agreement.doPhase(other, true);
        byte[] shared = agreement.generateSecret();

        byte[] salt = new byte[ephemeralPublic.length + recipientPublic.length];
        System.arraycopy(ephemeralPublic, 0, salt, 0, ephemeralPublic.length);
        System.arraycopy(recipientPublic, 0, salt, ephemeralPublic.length, recipientPublic.length);
        try {
            KDF hkdf = KDF.getInstance("HKDF-SHA256");
            return hkdf.deriveData(HKDFParameterSpec.ofExtract().addIKM(shared).addSalt(salt).thenExpand(INFO, 32));
        } finally {
            Arrays.fill(shared, (byte) 0);
        }
    }
}
