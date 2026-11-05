package com.server.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * RS256 JWT imzalama ve doğrulama. Aktif anahtar imzalar; doğrulama token başlığındaki kid'e göre
 * aktif anahtarı ve (tanımlıysa) bir önceki anahtarı kabul eder.
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        RSAKey signingKey = new RSAKey.Builder(readKey(properties.publicKeyPath(), RsaKeyConverters.x509()))
                .privateKey(readKey(properties.privateKeyPath(), RsaKeyConverters.pkcs8()))
                .keyID(properties.keyId())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        List<JWK> verificationKeys = new ArrayList<>();
        verificationKeys.add(publicKey(properties.publicKeyPath(), properties.keyId()));
        if (properties.hasPreviousKey()) {
            verificationKeys.add(publicKey(properties.previousPublicKeyPath(), properties.previousKeyId()));
        }

        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                JWSAlgorithm.RS256, new ImmutableJWKSet<>(new JWKSet(verificationKeys))));
        // Süre ve issuer kontrolünü Spring'in validator'ları yapar.
        processor.setJWTClaimsSetVerifier((claims, context) -> {
        });

        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private static RSAKey publicKey(String path, String keyId) {
        RSAPublicKey key = readKey(path, RsaKeyConverters.x509());
        return new RSAKey.Builder(key).keyID(keyId).build();
    }

    private static <K> K readKey(String path, Converter<InputStream, K> converter) {
        Path file = Path.of(path);
        if (!Files.isReadable(file)) {
            throw new IllegalStateException("JWT anahtar dosyası okunamadı: " + file.toAbsolutePath()
                    + " (anahtar üretimi için server/README.md)");
        }
        try (InputStream in = Files.newInputStream(file)) {
            return converter.convert(in);
        } catch (IOException e) {
            throw new IllegalStateException("JWT anahtar dosyası okunamadı: " + file.toAbsolutePath(), e);
        }
    }
}
