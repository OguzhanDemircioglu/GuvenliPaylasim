package com.server.modules.invite.dto;

/**
 * Gönderenin doğrulaması gereken kabul (Crypto_Design §7.6, "Gönderen, kabulden sonra"). İstemci {@code selfWrapped}'i
 * kendi özel anahtarıyla açıp mühürKey'i alır ({@code verifierHash} sarmalamanın bağlamıdır), alıcının güncel
 * anahtarlarından parmak izini hesaplar ve {@code seal} ile karşılaştırır.
 */
public record InviteVerificationResponse(Long id, String selfWrapped, String verifierHash, String seal,
                                         PartyResponse recipient) {
}
