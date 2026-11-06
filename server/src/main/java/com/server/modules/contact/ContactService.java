package com.server.modules.contact;

import com.server.modules.contact.dto.ContactResponse;

import java.util.Collection;
import java.util.List;

public interface ContactService {

    List<ContactResponse> list(Long ownerId);

    /** Kişilik iki yönde de silinir; tekrar göndermek için yeni davet kodu gerekir. */
    void delete(Long ownerId, Long contactId);

    /**
     * Kodsuz gönderim yetkisinin tek kontrol noktası (Crypto_Design §8.6: ileride teslim jetonuyla değiştirilebilir).
     * Her alıcı, göndereninin doğrulanmış kişisi olmalı ve kayıttaki parmak izi alıcının güncel anahtarıyla aynı
     * olmalıdır; değilse alıcının anahtarı değişmiştir ve yeni davet kodu gerekir. Uymayan varsa 422.
     */
    void requireVerifiedRecipients(Long senderId, Collection<Long> recipientIds);

    /** Kod girilince alıcının tarafı: göndereni doğrulanmış kişi olarak kaydeder (varsa günceller). */
    void saveVerified(Long ownerId, Long contactId, String fingerprint, String signature);

    /** Kod girilince göndericinin tarafı: kabul mührü doğrulanana kadar bekleyen kayıt. */
    void savePending(Long ownerId, Long contactId);

    /** Mühür tutmazsa bekleyen kayıt silinir; doğrulanmış kayda dokunulmaz. */
    void removePending(Long ownerId, Long contactId);
}
