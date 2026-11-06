package com.desktop.service;

import com.desktop.api.Dto;
import com.desktop.crypto.CryptoException;
import com.desktop.crypto.DocumentContent;
import com.desktop.crypto.InviteCrypto;
import tools.jackson.core.type.TypeReference;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * İlk temas: davet kodu (Crypto_Design §7.6). Kod bu bilgisayarda üretilir, ekranda gösterilir ve hiçbir yere
 * kaydedilmez. Kutunun şifrelenmesi, açılması ve iki yönlü doğrulama InviteCrypto'dadır.
 */
public final class InviteService {

    private static final TypeReference<Object> ANY = new TypeReference<>() {
    };

    /** Gönderenin ekranında gösterilecek kod. */
    public record SentCode(String code, String recipientPhone, Instant expiresAt) {
    }

    public record Redeemed(String senderName, Long documentId) {
    }

    /** Arka planda doğrulanan kabuller: kişi listesine eklenenler ve doğrulaması başarısız olanlar. */
    public record Verifications(List<String> verified, List<String> failed) {
    }

    private final Session session;

    public InviteService(Session session) {
        this.session = session;
    }

    /**
     * @param ttlHours      kodun geçerliliği: 1, 24, 72 ya da 168; kutunun ömründen uzun olamaz
     * @param lifetimeHours kutunun ömrü: 1, 24, 168 ya da 720
     */
    public SentCode send(DocumentContent content, String recipientPhone, int ttlHours, int lifetimeHours,
                         boolean deleteOnOpen) {
        String phone = Phones.normalize(recipientPhone);
        if (phone == null) {
            throw new IllegalArgumentException(AuthService.validatePhone(recipientPhone));
        }
        InviteCrypto.Created created = InviteCrypto.create(content, session.keys());
        Dto.InviteCreated result = session.api().post("/api/invites",
                new Dto.InviteCreateRequest(phone, ttlHours, content.text() ? Dto.DocumentKind.TEXT : Dto.DocumentKind.FILE,
                        Base64.getEncoder().encodeToString(created.payload()), created.signature(), created.wrapped(),
                        created.verifierHash(), created.selfWrapped(), lifetimeHours, deleteOnOpen),
                new TypeReference<>() {
                });
        return new SentCode(InviteCrypto.format(created.code()), Phones.toE164(phone), result.expiresAt());
    }

    /**
     * Kodu girer, kutuyu bu bilgisayarda açıp doğrular ve kabul eder. Gönderenin anahtarı kutudakiyle uyuşmazsa
     * (sunucu araya sahte anahtar koyduysa) kabul edilmez ve CryptoException fırlatılır.
     */
    public Redeemed redeem(String codeInput) {
        String code = InviteCrypto.normalize(codeInput);
        if (code == null) {
            throw new IllegalArgumentException("Kodu 16 karakter olarak yazın: XXXX-XXXX-XXXX-XXXX.");
        }
        Dto.InviteRedeem invite = session.api().post("/api/invites/redeem",
                new Dto.InviteRedeemRequest(InviteCrypto.verifier(code)), new TypeReference<>() {
                });
        Dto.Party sender = invite.sender();
        InviteCrypto.Accepted accepted = InviteCrypto.accept(code, Base64.getDecoder().decode(invite.payload()),
                invite.signature(), invite.wrapped(), sender.encryptionPublicKey(), sender.signingPublicKey(),
                sender.phone(), session.keys(), session.user().phone());
        Dto.InviteAccepted result = session.api().post("/api/invites/" + invite.id() + "/accept",
                new Dto.InviteAcceptRequest(accepted.wrappedKey(), accepted.seal(), accepted.senderFingerprint(),
                        accepted.contactSignature()),
                new TypeReference<>() {
                });
        return new Redeemed(sender.fullName(), result.documentId());
    }

    /**
     * Gönderdiği davetler kabul edilmişse alıcıyı doğrular (Crypto_Design §7.6, "Gönderen, kabulden sonra"). Mühür
     * tutmazsa kabul reddedilir ve kişi eklenmez. Gelen kutusu yoklamasıyla birlikte arka planda çalışır.
     */
    public Verifications processVerifications() {
        List<Dto.InviteVerification> pending = session.api().get("/api/invites/verifications", new TypeReference<>() {
        });
        List<String> verified = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Dto.InviteVerification v : pending) {
            Dto.Party recipient = v.recipient();
            String signature;
            try {
                signature = InviteCrypto.verifyAcceptance(v.selfWrapped(), v.verifierHash(), v.seal(),
                        recipient.encryptionPublicKey(), recipient.signingPublicKey(), recipient.phone(),
                        session.keys(), session.user().phone());
            } catch (CryptoException e) {
                session.api().post("/api/invites/" + v.id() + "/reject", null, ANY);
                failed.add(recipient.fullName());
                continue;
            }
            session.api().post("/api/invites/" + v.id() + "/verify", new Dto.InviteVerifyRequest(
                    com.desktop.crypto.UserKeys.fingerprint(recipient.encryptionPublicKey(), recipient.signingPublicKey()),
                    signature), ANY);
            verified.add(recipient.fullName());
        }
        return new Verifications(verified, failed);
    }

    public List<Dto.SentInvite> sent() {
        return session.api().get("/api/invites/sent", new TypeReference<>() {
        });
    }

    public void cancel(Long inviteId) {
        session.api().delete("/api/invites/" + inviteId);
    }
}
