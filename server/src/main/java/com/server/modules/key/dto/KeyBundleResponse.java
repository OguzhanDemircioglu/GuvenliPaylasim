package com.server.modules.key.dto;

import com.server.modules.key.UserKey;

import java.time.Instant;

public record KeyBundleResponse(String encryptionPublicKey, String signingPublicKey, String privateKeyBundle,
                                Instant createdAt) {

    public static KeyBundleResponse from(UserKey key) {
        return new KeyBundleResponse(key.getEncryptionPublicKey(), key.getSigningPublicKey(),
                key.getPrivateKeyBundle(), key.getCreatedAt());
    }
}
