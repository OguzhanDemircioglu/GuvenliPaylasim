package com.desktop.service;

import com.desktop.api.ApiException;
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

    /** {@code lifetimeHours}: 1, 24, 168 ya da 720; {@code deleteOnOpen}: açılınca sil (Crypto_Design §8). */
    public Dto.DocumentSent send(DocumentContent content, List<Dto.Recipient> recipients, int lifetimeHours,
                                 boolean deleteOnOpen) {
        DocumentCrypto.Sealed sealed = DocumentCrypto.seal(content, session.keys(), recipients.stream()
                .map(r -> new DocumentCrypto.Recipient(r.userId(), r.encryptionPublicKey()))
                .toList());
        List<Dto.RecipientKey> keys = sealed.wrappedKeys().entrySet().stream()
                .map(e -> new Dto.RecipientKey(e.getKey(), e.getValue()))
                .toList();
        Dto.DocumentSendRequest request = new Dto.DocumentSendRequest(
                content.text() ? Dto.DocumentKind.TEXT : Dto.DocumentKind.FILE,
                Base64.getEncoder().encodeToString(sealed.payload()), sealed.signature(), keys, lifetimeHours,
                deleteOnOpen);
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

    /** {@code removedFromServer}: "açılınca sil" kutusuydu ve açıldığı onaylanınca sunucudan silindi. */
    public record OpenResult(DocumentCrypto.Opened opened, boolean removedFromServer) {
    }

    /**
     * Belgeyi indirir, imzasını doğrular ve çözer; ilk açılışta sunucu belgeyi "okundu" işaretler. "Açılınca Sil"
     * kutusunda onay ancak içerik bu bilgisayarda çözülüp doğrulandıktan sonra gönderilir (Crypto_Design §8.3);
     * onay gönderilemezse kutu ömrü dolana kadar sunucuda kalır ve tekrar açılabilir.
     */
    public OpenResult open(long documentId) {
        Dto.DocumentPayload document = session.api().get("/api/documents/" + documentId, new TypeReference<>() {
        });
        DocumentCrypto.Opened opened = DocumentCrypto.open(Base64.getDecoder().decode(document.payload()),
                document.signature(), document.wrappedKey(), document.senderSigningPublicKey(), session.keys());
        boolean removed = false;
        if (document.deleteOnOpen()) {
            try {
                session.api().post("/api/documents/" + documentId + "/opened", null, new TypeReference<Object>() {
                });
                removed = true;
            } catch (ApiException e) {
                // Kutu sunucuda kalır; bir sonraki açılışta onay tekrar gönderilir.
            }
        }
        return new OpenResult(opened, removed);
    }

    /** "Okudum ve Sil": alıcının kutusu silinir; son alıcıysa belge sunucudan tamamen silinir. */
    public void delete(long documentId) {
        session.api().delete("/api/documents/" + documentId);
    }
}
