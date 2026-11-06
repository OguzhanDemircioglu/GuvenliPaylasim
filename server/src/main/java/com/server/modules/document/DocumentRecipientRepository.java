package com.server.modules.document;

import com.server.modules.document.dto.InboxItemResponse;
import com.server.modules.document.dto.SentRecipientResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentRecipientRepository extends JpaRepository<DocumentRecipient, Long> {

    /** Alıcının silmediği belgeler, yeniden eskiye. */
    @Query("""
            SELECT new com.server.modules.document.dto.InboxItemResponse(
                       d.id, d.kind, d.sizeBytes, s.id, s.fullName, d.createdAt, r.readAt)
              FROM DocumentRecipient r
              JOIN Document d ON d.id = r.documentId
              JOIN User s ON s.id = d.senderId
             WHERE r.recipientId = :recipientId AND r.deletedAt IS NULL
             ORDER BY d.createdAt DESC""")
    List<InboxItemResponse> findInbox(Long recipientId);

    Optional<DocumentRecipient> findByDocumentIdAndRecipientIdAndDeletedAtIsNull(Long documentId, Long recipientId);

    boolean existsByDocumentIdAndDeletedAtIsNull(Long documentId);

    @Query("""
            SELECT new com.server.modules.document.dto.SentRecipientResponse(
                       r.documentId, u.id, u.fullName, r.readAt, r.deletedAt)
              FROM DocumentRecipient r
              JOIN User u ON u.id = r.recipientId
             WHERE r.documentId IN :documentIds
             ORDER BY u.fullName""")
    List<SentRecipientResponse> findSentRecipients(Collection<Long> documentIds);
}
