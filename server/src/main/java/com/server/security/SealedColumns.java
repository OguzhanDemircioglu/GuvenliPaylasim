package com.server.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * İkinci kilitle saklanan kolonlar (ServerSeal). Converter'lar Spring bean'idir; Hibernate onları Spring'den alır
 * (Spring Boot'un SpringBeanContainer ayarı). Bağlam (AAD) kolonun adıdır.
 */
public final class SealedColumns {

    public static final String PASSWORD_HASH = "users.password_hash";
    public static final String PRIVATE_KEY_BUNDLE = "user_keys.private_key_bundle";

    private SealedColumns() {
    }

    @Component
    @Converter
    public static class PasswordHash implements AttributeConverter<String, String> {

        private final ServerSeal seal;

        public PasswordHash(ServerSeal seal) {
            this.seal = seal;
        }

        @Override
        public String convertToDatabaseColumn(String attribute) {
            return seal.seal(attribute, PASSWORD_HASH);
        }

        @Override
        public String convertToEntityAttribute(String column) {
            return seal.open(column, PASSWORD_HASH);
        }
    }

    @Component
    @Converter
    public static class PrivateKeyBundle implements AttributeConverter<String, String> {

        private final ServerSeal seal;

        public PrivateKeyBundle(ServerSeal seal) {
            this.seal = seal;
        }

        @Override
        public String convertToDatabaseColumn(String attribute) {
            return seal.seal(attribute, PRIVATE_KEY_BUNDLE);
        }

        @Override
        public String convertToEntityAttribute(String column) {
            return seal.open(column, PRIVATE_KEY_BUNDLE);
        }
    }
}
