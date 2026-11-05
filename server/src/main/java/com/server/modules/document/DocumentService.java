package com.server.modules.document;

import com.server.modules.document.dto.DocumentContentResponse;
import com.server.modules.document.dto.DocumentSendRequest;
import com.server.modules.document.dto.DocumentSentResponse;
import com.server.modules.document.dto.InboxItemResponse;
import com.server.modules.document.dto.SentItemResponse;

import java.util.List;

/** Sunucu belgelerin içeriğini hiçbir zaman çözemez; burada yalnızca şifreli veri ve erişim kuralları vardır. */
public interface DocumentService {

    DocumentSentResponse send(Long senderId, DocumentSendRequest request);

    List<InboxItemResponse> inbox(Long recipientId);

    /** Belgeyi alıcıya verir ve ilk açılışta okundu olarak işaretler. Alıcı değilse 404. */
    DocumentContentResponse open(Long documentId, Long recipientId);

    /** "Okudum ve sil". Son alıcı da sildiğinde şifreli içerik veritabanından kalıcı olarak silinir. */
    void delete(Long documentId, Long recipientId);

    List<SentItemResponse> sent(Long senderId);
}
