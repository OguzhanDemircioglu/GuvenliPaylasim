package com.desktop.service;

import com.desktop.api.Dto;
import com.desktop.crypto.DocumentContent;
import com.desktop.crypto.DocumentCrypto;
import tools.jackson.core.type.TypeReference;

import java.util.Base64;
import java.util.List;

/** Belge gönderme ve açma. Şifreleme ve çözme bu bilgisayarda yapılır; sunucuya sadece şifreli veri gider. */
public final class DocumentService {

    /** Sunucudaki sınırla aynı (DocumentSendRequest.payload). */
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final Session session;

    public DocumentService(Session session) {
        this.session = session;
    }

    public Dto.DocumentSent send(DocumentContent content, List<Dto.Recipient> recipients) {
        DocumentCrypto.Sealed sealed = DocumentCrypto.seal(content, session.keys(), recipients.stream()
                .map(r -> new DocumentCrypto.Recipient(r.userId(), r.encryptionPublicKey()))
                .toList());
        List<Dto.RecipientKey> keys = sealed.wrappedKeys().entrySet().stream()
                .map(e -> new Dto.RecipientKey(e.getKey(), e.getValue()))
                .toList();
        Dto.DocumentSendRequest request = new Dto.DocumentSendRequest(
                content.text() ? Dto.DocumentKind.TEXT : Dto.DocumentKind.FILE,
                Base64.getEncoder().encodeToString(sealed.payload()), sealed.signature(), keys);
        return session.api().post("/api/documents", request, new TypeReference<>() {
        });
    }

    public List<Dto.InboxItem> inbox() {
        return session.api().get("/api/documents/inbox", new TypeReference<>() {
        });
    }

    public List<Dto.SentItem> sent() {
        return session.api().get("/api/documents/sent", new TypeReference<>() {
        });
    }

    /** Belgeyi indirir, imzasını doğrular ve çözer. İlk açılışta sunucu belgeyi "okundu" işaretler. */
    public DocumentCrypto.Opened open(long documentId) {
        Dto.DocumentPayload document = session.api().get("/api/documents/" + documentId, new TypeReference<>() {
        });
        return DocumentCrypto.open(Base64.getDecoder().decode(document.payload()), document.signature(),
                document.wrappedKey(), document.senderSigningPublicKey(), session.keys());
    }

    /** "Okudum ve sil": son alıcı da silince şifreli içerik sunucudan kalıcı olarak silinir. */
    public void delete(long documentId) {
        session.api().delete("/api/documents/" + documentId);
    }
}
