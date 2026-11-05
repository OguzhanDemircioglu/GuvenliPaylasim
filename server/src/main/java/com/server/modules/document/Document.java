package com.server.modules.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Şifreli belge. Teknik tablo: soft delete kullanmaz. Bütün alıcılar "okudum ve sil" dediğinde şifreli içerik ve
 * imza kalıcı olarak silinir ({@code purgedAt}); kim, kime, ne zaman bilgisi denetim için kalır.
 */
@Getter
@Setter
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private DocumentKind kind;

    /**
     * AES-256-GCM ile şifrelenmiş içerik (istemcinin biçimi). Silindiğinde null olur. PostgreSQL {@code bytea}.
     */
    @Column(name = "payload", columnDefinition = "bytea")
    private byte[] payload;

    /** Gönderenin Ed25519 imzası (Base64). */
    @Column(name = "signature", length = 200)
    private String signature;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "purged_at")
    private Instant purgedAt;
}
