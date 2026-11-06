package com.server.modules.document;

import com.server.modules.document.dto.InboxItemResponse;
import com.server.modules.document.dto.SentRecipientResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentRecipientRepository extends JpaRepository<DocumentRecipient, Long> {

    /** Alıcının ömrü dolmamış belgeleri, yeniden eskiye. */
    @Query("""
            SELECT new com.server.modules.document.dto.InboxItemResponse(
                       d.id, d.kind, d.sizeBytes, s.id, s.fullName, d.createdAt, d.expiresAt, d.deleteOnOpen, r.readAt)
              FROM DocumentRecipient r
              JOIN Document d ON d.id = r.documentId
              JOIN User s ON s.id = d.senderId
             WHERE r.recipientId = :recipientId AND d.expiresAt > :now
             ORDER BY d.createdAt DESC""")
    List<InboxItemResponse> findInbox(Long recipientId, Instant now);

    Optional<DocumentRecipient> findByDocumentIdAndRecipientId(Long documentId, Long recipientId);

    List<DocumentRecipient> findByRecipientId(Long recipientId);

    boolean existsByDocumentId(Long documentId);

    @Query("""
            SELECT new com.server.modules.document.dto.SentRecipientResponse(r.documentId, u.id, u.fullName, r.readAt)
              FROM DocumentRecipient r
              JOIN User u ON u.id = r.recipientId
             WHERE r.documentId IN :documentIds
             ORDER BY u.fullName""")
    List<SentRecipientResponse> findSentRecipients(Collection<Long> documentIds);

    /** Ömrü dolan belgelerin alıcı satırları (Crypto_Design §8.2). */
    @Transactional
    @Modifying
    @Query("delete from DocumentRecipient r where r.documentId in (select d.id from Document d where d.expiresAt < :now)")
    int deleteExpired(Instant now);
}
