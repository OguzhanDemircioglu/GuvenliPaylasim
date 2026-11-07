package com.server.modules.document;

import com.server.modules.document.dto.SentDocumentRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    /** Gönderenin ömrü dolmamış kutuları, yeniden eskiye. İçerik (en fazla 10 MB) okunmaz; liste sık yenilenir. */
    @Query("""
            SELECT new com.server.modules.document.dto.SentDocumentRow(
                       d.id, d.kind, d.sizeBytes, d.createdAt, d.expiresAt, d.deleteOnOpen)
              FROM Document d
             WHERE d.senderId = :senderId AND d.expiresAt > :now
             ORDER BY d.createdAt DESC""")
    List<SentDocumentRow> findSent(Long senderId, Instant now);

    long countBySenderIdAndCreatedAtAfter(Long senderId, Instant after);

    /** Gönderenin ömrü dolmamış kutularının toplam boyutu (kota, Crypto_Design §7.9). */
    @Query("select coalesce(sum(d.sizeBytes), 0) from Document d where d.senderId = :senderId and d.expiresAt > :now")
    long sumLiveSize(Long senderId, Instant now);

    @Transactional
    @Modifying
    @Query("delete from Document d where d.expiresAt < :now")
    int deleteExpired(Instant now);
}
