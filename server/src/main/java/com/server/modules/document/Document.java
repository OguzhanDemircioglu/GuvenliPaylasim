package com.server.modules.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

/**
 * Şifreli belge. Teknik tablo: soft delete kullanmaz. Crypto_Design §8: her kutunun bir ömrü vardır
 * ({@code expiresAt}); süre dolunca, son alıcı silince ya da "açılınca sil" kutusu açılınca satır alıcı satırlarıyla
 * birlikte tamamen silinir. Kim, kime, ne zaman bilgisi de kalmaz.
 */
@Getter
@Setter
@Entity
// Gönderilenler (gönderen + ömür) ve temizlik işi (ömür) için.
@Table(name = "documents", indexes = {
        @Index(name = "ix_documents_sender", columnList = "sender_id, expires_at"),
        @Index(name = "ix_documents_expires", columnList = "expires_at")})
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private DocumentKind kind;

    /** AES-256-GCM ile şifrelenmiş içerik (istemcinin biçimi). PostgreSQL {@code bytea}. */
    @Column(name = "payload", columnDefinition = "bytea")
    private byte[] payload;

    /** Gönderenin Ed25519 imzası (Base64). */
    @Column(name = "signature", length = 200)
    private String signature;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Kutunun ömrünün bittiği an. DB varsayılanı sadece bu kolon eklenmeden önce yazılmış satırlar içindir
     * (Backend_Architecture §11 kural 5); yeni satırlarda her zaman gönderim anı + seçilen ömür yazılır.
     */
    @ColumnDefault("(now() + interval '7 days')")
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Alıcı açtığını onaylayınca kendi satırı silinir (Crypto_Design §8.3). */
    @ColumnDefault("false")
    @Column(name = "delete_on_open", nullable = false)
    private boolean deleteOnOpen;
}
