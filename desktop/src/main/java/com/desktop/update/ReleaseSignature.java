package com.desktop.update;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Sürüm imzası (docs/Update_Design.md). İmzalanan metin sürümü, kurulum dosyasının SHA-256 özetini ve zorunluluk
 * bilgisini birlikte bağlar; böylece GitHub hesabı ele geçirilse bile imzasız ya da eski bir kurulum dosyası
 * kurdurulamaz. Özel anahtar sadece sürümü yayımlayanda durur; uygulamada açık anahtar vardır.
 */
public final class ReleaseSignature {

    private ReleaseSignature() {
    }

    static String signedText(String version, String sha256, boolean mandatory) {
        return "gps-release/v1|" + Version.parse(version) + "|" + sha256.toLowerCase() + "|" + mandatory;
    }

    public static String sign(PrivateKey key, String version, String sha256, boolean mandatory)
            throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key);
        signature.update(signedText(version, sha256, mandatory).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    static boolean verify(PublicKey key, String version, String sha256, boolean mandatory, String signatureBase64) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(key);
            signature.update(signedText(version, sha256, mandatory).getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    public static PublicKey publicKey(String base64) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64.trim())));
    }

    public static PrivateKey privateKey(String base64) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64.trim())));
    }
}
