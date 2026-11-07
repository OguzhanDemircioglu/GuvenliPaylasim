package com.server.modules.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Bir yönlü kişi kaydı: {@code owner} kullanıcısının listesinde {@code contact} (Crypto_Design §7.7). İki taraf
 * birbirini kişi olarak görür, bu yüzden her kişilik iki satırdır.
 * <p>
 * {@code fingerprint}: kayıt sahibinin doğruladığı parmak izi. {@code signature}: sahibinin Ed25519 imzası
 * ({@code "gps-contact/v1|" + sahip telefonu + "|" + kişi telefonu + "|" + parmak izi}). İstemci kendi imzası tutmayan
 * kaydı kabul etmez; böylece kayıt sunucuda dursa da sunucu parmak izini değiştiremez.
 * Teknik tablo: BaseEntity'den türemez.
 */
@Getter
@Setter
@Entity
@Table(name = "contacts",
        uniqueConstraints = @UniqueConstraint(name = "ux_contacts_owner_contact", columnNames = {"owner_id", "contact_id"}),
        // Hesap silinirken "contact_id = ?" ile aranır; unique constraint sadece owner_id ile başlayan aramalara yarar.
        indexes = @Index(name = "ix_contacts_contact", columnList = "contact_id"))
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContactStatus status;

    @Column(name = "fingerprint", length = 39)
    private String fingerprint;

    @Column(name = "signature", length = 200)
    private String signature;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
