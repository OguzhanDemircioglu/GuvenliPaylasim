package com.server.modules.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Kabul edilen her kod isteği. Numara, IP ve sistem geneli sınırlar bu kayıtlar sayılarak uygulanır (Security.md S8).
 * Numara kayıtlı olsun olmasın her istek aynı şekilde yazılır; böylece sınırlar da numaranın kayıtlı olup olmadığını
 * ele vermez. 1 günden eski kayıtlar silinir. Teknik tablo: BaseEntity'den türemez.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "verification_requests", indexes = {
        @Index(name = "ix_verification_requests_phone", columnList = "phone, created_at"),
        @Index(name = "ix_verification_requests_ip", columnList = "ip, created_at"),
        @Index(name = "ix_verification_requests_created", columnList = "created_at")})
public class VerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "phone", nullable = false, length = 13)
    private String phone;

    @Column(name = "ip", nullable = false, length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public VerificationRequest(String phone, String ip, Instant createdAt) {
        this.phone = phone;
        this.ip = ip;
        this.createdAt = createdAt;
    }
}
