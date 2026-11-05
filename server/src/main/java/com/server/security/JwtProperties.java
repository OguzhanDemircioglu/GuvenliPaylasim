package com.server.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT ayarları (app.jwt). Anahtarlar PEM dosyalarından okunur; uygulama anahtar üretmez.
 *
 * @param previousKeyId         anahtar değişimi sırasında bir önceki anahtarın kimliği (isteğe bağlı)
 * @param previousPublicKeyPath bir önceki public key; tanımlıysa onunla imzalanmış token'lar da kabul edilir
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        String issuer,
        Duration accessTokenTtl,
        String keyId,
        String privateKeyPath,
        String publicKeyPath,
        String previousKeyId,
        String previousPublicKeyPath) {

    public boolean hasPreviousKey() {
        return previousKeyId != null && !previousKeyId.isBlank()
                && previousPublicKeyPath != null && !previousPublicKeyPath.isBlank();
    }
}
