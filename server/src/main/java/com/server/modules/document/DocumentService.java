package com.server.modules.document;

import com.server.modules.document.dto.DocumentContentResponse;
import com.server.modules.document.dto.DocumentSendRequest;
import com.server.modules.document.dto.DocumentSentResponse;
import com.server.modules.document.dto.InboxItemResponse;
import com.server.modules.document.dto.SentItemResponse;

import java.util.List;

/** Sunucu belgelerin içeriğini hiçbir zaman çözemez; burada yalnızca şifreli veri ve erişim kuralları vardır. */
public interface DocumentService {

    /** Kodsuz gönderim: bütün alıcılar gönderenin doğrulanmış kişileri olmalıdır (Crypto_Design §7.7). */
    DocumentSentResponse send(Long senderId, DocumentSendRequest request);

    /**
     * Davet kabul edilince (Crypto_Design §7.6) kodla kilitli kutu, alıcının kendi anahtarına sarmalanmış normal bir
     * belgeye dönüşür. Yetki kontrolü davet akışında yapılmıştır.
     */
    Long createFromInvite(Long senderId, Long recipientId, DocumentKind kind, byte[] payload, String signature,
                          String wrappedKey, java.time.Instant expiresAt, boolean deleteOnOpen);

    List<InboxItemResponse> inbox(Long recipientId);

    /** Belgeyi alıcıya verir ve ilk açılışta okundu olarak işaretler. Alıcı değilse 404. */
    DocumentContentResponse open(Long documentId, Long recipientId);

    /** "Okudum ve sil": alıcının satırı silinir; son alıcıysa belge de tamamen silinir (Crypto_Design §8.5). */
    void delete(Long documentId, Long recipientId);

    /**
     * Alıcı kutuyu açıp doğruladığını onaylar (Crypto_Design §8.3, ikinci adım). Kutu "açılınca sil" ise alıcının
     * satırı burada silinir; değilse bir şey değişmez. Onay gelmezse kutu ömrü dolana kadar durur.
     */
    void opened(Long documentId, Long recipientId);

    /** Parola sıfırlamada (Crypto_Design §7.5): kullanıcıya gelmiş bütün kutular, tek tek silinmiş gibi silinir. */
    void deleteAllReceived(Long recipientId);

    List<SentItemResponse> sent(Long senderId);
}
