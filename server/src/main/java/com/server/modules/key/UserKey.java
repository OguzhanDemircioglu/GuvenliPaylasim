package com.server.modules.key;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Kullanıcının anahtar çifti. Teknik tablo: BaseEntity'den türemez, soft delete kullanmaz (Backend_Architecture §4.1).
 * <p>
 * Açık anahtarlar herkes tarafından okunabilir. Özel anahtarlar istemcide, paroladan türetilen ve sunucuya hiç
 * gelmeyen bir anahtarla şifrelenmiştir ({@code privateKeyBundle}); sunucu bu paketi açamaz.
 */
@Getter
@Setter
@Entity
@Table(name = "user_keys")
public class UserKey {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** X25519 açık anahtarı (X.509, Base64). Belge anahtarını bu kullanıcıya sarmalamak için kullanılır. */
    @Column(name = "encryption_public_key", nullable = false, length = 200)
    private String encryptionPublicKey;

    /** Ed25519 açık anahtarı (X.509, Base64). Bu kullanıcının imzalarını doğrulamak için kullanılır. */
    @Column(name = "signing_public_key", nullable = false, length = 200)
    private String signingPublicKey;

    /** AES-256-GCM ile şifrelenmiş özel anahtarlar (istemcinin biçimi, sunucu için opak). */
    @Column(name = "private_key_bundle", nullable = false, length = 1000)
    private String privateKeyBundle;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
