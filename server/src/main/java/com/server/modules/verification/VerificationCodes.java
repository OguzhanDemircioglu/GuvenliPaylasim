package com.server.modules.verification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** 6 haneli doğrulama kodu üretimi ve özeti. */
final class VerificationCodes {

    private static final SecureRandom RANDOM = new SecureRandom();

    private VerificationCodes() {
    }

    static String generate() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    /**
     * Kod veritabanına açık yazılmaz. Özete numara ve amaç da katılır; bir kayıttaki özet başka bir numara ya da amaç
     * için kullanılamaz. Not: 6 hane deneme yoluyla bulunabilir; koruma kısa ömür (5 dk) ve 3 deneme sınırıdır.
     */
    static String hash(String phone, VerificationPurpose purpose, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((purpose.name() + "|" + phone + "|" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Sabit zamanlı karşılaştırma. */
    static boolean matches(String expectedHash, String phone, VerificationPurpose purpose, String code) {
        return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
                hash(phone, purpose, code).getBytes(StandardCharsets.US_ASCII));
    }
}
