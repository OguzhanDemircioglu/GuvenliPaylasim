package com.server.modules.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Bir belgenin bir alıcısı. Belge anahtarı bu alıcının açık anahtarıyla sarmalanmıştır ({@code wrappedKey});
 * sadece alıcının özel anahtarı açabilir. Alıcı belgeyi sildiğinde sarmalanmış anahtar da silinir.
 */
@Getter
@Setter
@Entity
// Benzersizlik kısıtı document_id ile başlar; gelen kutusu alıcıya göre arar.
@Table(name = "document_recipients",
        uniqueConstraints = @UniqueConstraint(name = "ux_document_recipients", columnNames = {"document_id", "recipient_id"}),
        indexes = @Index(name = "ix_document_recipients_recipient", columnList = "recipient_id"))
public class DocumentRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "wrapped_key", length = 300)
    private String wrappedKey;

    @Column(name = "read_at")
    private Instant readAt;
}
