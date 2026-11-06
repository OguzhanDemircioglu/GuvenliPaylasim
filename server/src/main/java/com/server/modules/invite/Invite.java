package com.server.modules.invite;

import com.server.modules.document.DocumentKind;
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
 * Davet koduyla kilitli ilk kutu (docs/Crypto_Design.md §7.6). Kod ve koddan türetilen anahtarlar sunucuya hiç gelmez;
 * burada sadece {@code SHA-256(doğrulayıcı)} ({@code verifierHash}) ve kodla şifrelenmiş içerik durur.
 * <p>
 * Yaşam döngüsü: oluşturulur → alıcı kodu girip kabul eder (içerik normal belgeye dönüşür, burada silinir) →
 * gönderenin istemcisi kabul mührünü doğrular ya da reddeder. Süresi dolan ve tamamlanan davetler zamanlanmış işle
 * silinir (InviteCleanupJob). Teknik tablo: BaseEntity'den türemez.
 */
@Getter
@Setter
@Entity
@Table(name = "invites", indexes = {
        @Index(name = "ix_invites_recipient", columnList = "recipient_phone, verifier_hash"),
        @Index(name = "ix_invites_sender", columnList = "sender_id, created_at")})
public class Invite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /** E.164. Davet bir numaraya bırakılır; alıcının kayıtlı olması gerekmez. */
    @Column(name = "recipient_phone", nullable = false, length = 13)
    private String recipientPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private DocumentKind kind;

    /** Kabulde belgeye taşınır ve burada null olur. */
    @Column(name = "payload", columnDefinition = "bytea")
    private byte[] payload;

    @Column(name = "signature", length = 200)
    private String signature;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** {@code 0x01 | AES-256-GCM(kodKey, K || gönderenin parmak izi, AAD = SHA-256(payload))}, Base64. */
    @Column(name = "wrapped", length = 200)
    private String wrapped;

    /** {@code SHA-256(doğrulayıcı)}, hex. */
    @Column(name = "verifier_hash", nullable = false, length = 64)
    private String verifierHash;

    /** Kabul mührünü doğrulayacak anahtar, gönderenin kendi açık anahtarına sarmalı (Base64). Kod saklanmaz. */
    @Column(name = "self_wrapped", nullable = false, length = 300)
    private String selfWrapped;

    /** Davetin (kodun) son geçerlilik zamanı. Kutunun ömründen uzun olamaz. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Kutunun ömrü gönderim anından sayılır (Crypto_Design §8.2); kod girilince belge bu zamanla oluşur.
     * DB varsayılanı sadece kolon eklenmeden önceki satırlar içindir.
     */
    @ColumnDefault("(now() + interval '7 days')")
    @Column(name = "box_expires_at", nullable = false)
    private Instant boxExpiresAt;

    @ColumnDefault("false")
    @Column(name = "delete_on_open", nullable = false)
    private boolean deleteOnOpen;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "accepted_by")
    private Long acceptedBy;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /** {@code HMAC-SHA256(mühürKey, "gps-invite-accept/v1" || gönderen parmak izi || alıcı parmak izi)}, Base64. */
    @Column(name = "accept_seal", length = 100)
    private String acceptSeal;

    @Column(name = "document_id")
    private Long documentId;

    /** Gönderen mührü doğruladı ya da reddetti. */
    @Column(name = "completed_at")
    private Instant completedAt;
}
