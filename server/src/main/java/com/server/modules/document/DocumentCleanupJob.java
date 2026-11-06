package com.server.modules.document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Ömrü dolan kutular, açılmış olsun olmasın, alıcı satırlarıyla birlikte kalıcı olarak silinir (Crypto_Design §8.2).
 * En kısa ömür 1 saat olduğu için 10 dakikada bir çalışmak yeterlidir; arada kalan kutular sorgularda zaten görünmez.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentCleanupJob {

    private final DocumentRepository documentRepository;
    private final DocumentRecipientRepository recipientRepository;

    @Scheduled(fixedDelay = 10, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void cleanup() {
        Instant now = Instant.now();
        int recipients = recipientRepository.deleteExpired(now);
        int documents = documentRepository.deleteExpired(now);
        if (documents > 0) {
            log.info("Document cleanup: {} expired documents ({} recipient rows) deleted", documents, recipients);
        }
    }
}
