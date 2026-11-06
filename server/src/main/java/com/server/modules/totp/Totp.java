package com.server.modules.totp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * TOTP (RFC 6238): HMAC-SHA1, 30 saniye, 6 hane. Authenticator uygulamalarının varsayılanı budur. Anahtar 160 bit,
 * Base32 (RFC 4648) olarak gösterilir.
 */
final class Totp {

    static final int PERIOD_SECONDS = 30;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    static byte[] newSecret() {
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        return secret;
    }

    static long currentStep() {
        return System.currentTimeMillis() / 1000 / PERIOD_SECONDS;
    }

    /** RFC 6238 / RFC 4226: zaman adımına göre 6 haneli kod. */
    static String code(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return "%06d".formatted(binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 not available", e);
        }
    }

    /**
     * Bir önceki, şimdiki ve bir sonraki adımda (±30 sn saat kayması) eşleşen ve {@code afterStep}'ten büyük adım;
     * yoksa -1. Aynı kod bir daha kullanılamaz (adım ileri gitmeli).
     */
    static long matchingStep(byte[] secret, String code, long now, long afterStep) {
        byte[] given = code.getBytes(StandardCharsets.US_ASCII);
        long found = -1;
        for (long step = now - 1; step <= now + 1; step++) {
            boolean equal = MessageDigest.isEqual(given, code(secret, step).getBytes(StandardCharsets.US_ASCII));
            if (equal && step > afterStep && found < 0) {
                found = step;
            }
        }
        return found;
    }

    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] fromBase32(String text) {
        ByteBuffer out = ByteBuffer.allocate(text.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : text.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base32");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }

    /** Authenticator uygulamalarının okuduğu adres (QR kodun içeriği). */
    static String uri(String issuer, String account, String base32Secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=6&period=" + PERIOD_SECONDS;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
