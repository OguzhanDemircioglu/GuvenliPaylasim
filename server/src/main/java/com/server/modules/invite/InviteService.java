package com.server.modules.invite;

import com.server.modules.invite.dto.InviteAcceptRequest;
import com.server.modules.invite.dto.InviteAcceptedResponse;
import com.server.modules.invite.dto.InviteCreateRequest;
import com.server.modules.invite.dto.InviteCreatedResponse;
import com.server.modules.invite.dto.InviteRedeemResponse;
import com.server.modules.invite.dto.InviteVerificationResponse;
import com.server.modules.invite.dto.InviteVerifyRequest;
import com.server.modules.invite.dto.SentInviteResponse;

import java.util.List;

/** İlk temas: davet kodu (docs/Crypto_Design.md §7.6). Kod sunucuya hiç gelmez. */
public interface InviteService {

    /** Numara kayıtlı olsun olmasın aynı cevap; gönderen numaranın kayıtlı olup olmadığını öğrenmez. */
    InviteCreatedResponse create(Long senderId, InviteCreateRequest request);

    /**
     * Kodu giren kullanıcıya, sadece onun telefonuna bırakılmış davet verilir. Yanlış kod sayılır: 5 hatalı denemeden
     * sonra 1 saat bekleme. Bulunamayan, süresi dolan ya da başkasına ait davet aynı cevabı alır.
     */
    InviteRedeemResponse redeem(Long recipientId, String verifierBase64);

    /** Kutu alıcının gelen kutusuna geçer; iki taraf birbirinin kişi listesine eklenir (gönderen tarafı bekler). */
    InviteAcceptedResponse accept(Long recipientId, Long inviteId, InviteAcceptRequest request);

    List<InviteVerificationResponse> pendingVerifications(Long senderId);

    /** Mühür tuttu: gönderenin kişi kaydı doğrulanır. */
    void verify(Long senderId, Long inviteId, InviteVerifyRequest request);

    /** Mühür tutmadı (sunucu araya sahte anahtar koymuş olabilir): bekleyen kişi kaydı silinir. */
    void reject(Long senderId, Long inviteId);

    List<SentInviteResponse> sent(Long senderId);

    /** Kabul edilmemiş daveti gönderen geri alır; içerik kalıcı olarak silinir. */
    void cancel(Long senderId, Long inviteId);
}
