package com.server.modules.invite;

import com.server.common.exception.BusinessException;
import com.server.common.exception.LoginBlockedException;
import com.server.common.exception.NotFoundException;
import com.server.common.message.GlobalMessages;
import com.server.modules.auth.LoginAttemptRepository;
import com.server.modules.auth.LoginProtectionProperties;
import com.server.modules.contact.ContactMessages;
import com.server.modules.contact.ContactService;
import com.server.modules.document.DocumentMessages;
import com.server.modules.document.DocumentService;
import com.server.modules.invite.dto.InviteAcceptRequest;
import com.server.modules.invite.dto.InviteAcceptedResponse;
import com.server.modules.invite.dto.InviteCreateRequest;
import com.server.modules.invite.dto.InviteCreatedResponse;
import com.server.modules.invite.dto.InviteRedeemResponse;
import com.server.modules.invite.dto.InviteVerificationResponse;
import com.server.modules.invite.dto.InviteVerifyRequest;
import com.server.modules.invite.dto.PartyResponse;
import com.server.modules.invite.dto.SentInviteResponse;
import com.server.modules.key.Fingerprints;
import com.server.modules.key.UserKey;
import com.server.modules.key.UserKeyRepository;
import com.server.modules.user.Phones;
import com.server.modules.user.User;
import com.server.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * redeem bilerek transaction içinde değildir: yanlış kod sayacı, hemen ardından fırlatılan exception ile geri
 * alınmamalıdır (AuthServiceImpl ile aynı gerekçe).
 */
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    /** Crypto_Design §7.6: 1 saat, 1 gün, 3 gün, 7 gün. */
    static final Set<Integer> TTL_HOURS = Set.of(1, 24, 72, 168);
    /** Crypto_Design §7.9. */
    static final int DAILY_LIMIT = 20;
    private static final String REDEEM_KEY_PREFIX = "invite_redeem_";

    private final InviteRepository repository;
    private final UserRepository userRepository;
    private final UserKeyRepository keyRepository;
    private final ContactService contactService;
    private final DocumentService documentService;
    private final LoginAttemptRepository attemptRepository;
    private final LoginProtectionProperties protection;

    @Override
    @Transactional
    public InviteCreatedResponse create(Long senderId, InviteCreateRequest request) {
        if (!TTL_HOURS.contains(request.ttlHours())) {
            throw new BusinessException(InviteMessages.TTL_INVALID);
        }
        if (!keyRepository.existsById(senderId)) {
            throw new BusinessException(DocumentMessages.SENDER_HAS_NO_KEYS);
        }
        User sender = userRepository.findById(senderId).orElseThrow(InviteServiceImpl::notFound);
        String recipientPhone = Phones.toE164(request.recipientPhone());
        if (recipientPhone.equals(sender.getPhone())) {
            throw new BusinessException(InviteMessages.OWN_PHONE);
        }
        Instant now = Instant.now();
        if (repository.countBySenderIdAndCreatedAtAfter(senderId, now.minus(Duration.ofDays(1))) >= DAILY_LIMIT) {
            throw new BusinessException(InviteMessages.DAILY_LIMIT);
        }

        Invite invite = new Invite();
        invite.setSenderId(senderId);
        invite.setRecipientPhone(recipientPhone);
        invite.setKind(request.kind());
        invite.setPayload(decode(request.payload()));
        invite.setSizeBytes(invite.getPayload().length);
        invite.setSignature(request.signature());
        invite.setWrapped(request.wrapped());
        invite.setVerifierHash(request.verifierHash());
        invite.setSelfWrapped(request.selfWrapped());
        invite.setCreatedAt(now);
        invite.setExpiresAt(now.plus(Duration.ofHours(request.ttlHours())));
        repository.save(invite);
        return new InviteCreatedResponse(invite.getId(), invite.getExpiresAt());
    }

    @Override
    public InviteRedeemResponse redeem(Long recipientId, String verifierBase64) {
        String attemptKey = REDEEM_KEY_PREFIX + recipientId;
        Instant now = Instant.now();
        if (attemptRepository.findById(attemptKey).filter(a -> a.isBlocked(now)).isPresent()) {
            throw new LoginBlockedException(InviteMessages.REDEEM_BLOCKED);
        }
        User recipient = userRepository.findById(recipientId).orElseThrow(InviteServiceImpl::notFound);
        Invite invite = verifierHash(verifierBase64)
                .flatMap(hash -> repository.findRedeemable(recipient.getPhone(), hash, now))
                .orElse(null);
        UserKey senderKey = invite == null ? null : keyRepository.findById(invite.getSenderId()).orElse(null);
        if (invite == null || senderKey == null) {
            attemptRepository.recordFailure(attemptKey, now, now.plus(protection.blockDuration()), protection.maxAttempts());
            throw new BusinessException(InviteMessages.CODE_INVALID);
        }
        attemptRepository.deleteById(attemptKey);
        return new InviteRedeemResponse(invite.getId(), invite.getKind(),
                Base64.getEncoder().encodeToString(invite.getPayload()), invite.getSignature(), invite.getWrapped(),
                invite.getCreatedAt(), party(invite.getSenderId(), senderKey));
    }

    @Override
    @Transactional
    public InviteAcceptedResponse accept(Long recipientId, Long inviteId, InviteAcceptRequest request) {
        User recipient = userRepository.findById(recipientId).orElseThrow(InviteServiceImpl::notFound);
        Invite invite = repository.findById(inviteId)
                .filter(i -> i.getRecipientPhone().equals(recipient.getPhone()))
                .filter(i -> i.getAcceptedAt() == null && i.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(InviteServiceImpl::notFound);
        if (!keyRepository.existsById(recipientId)) {
            throw new BusinessException(DocumentMessages.SENDER_HAS_NO_KEYS);
        }
        UserKey senderKey = keyRepository.findById(invite.getSenderId()).orElseThrow(InviteServiceImpl::notFound);
        // Alıcı göndereni kutunun içindeki parmak iziyle doğruladı; kaydedilen değer güncel anahtarla aynı olmalı.
        if (!Fingerprints.of(senderKey).equals(request.fingerprint())) {
            throw new BusinessException(ContactMessages.FINGERPRINT_MISMATCH);
        }

        Long documentId = documentService.createFromInvite(invite.getSenderId(), recipientId, invite.getKind(),
                invite.getPayload(), invite.getSignature(), request.wrappedKey());
        contactService.saveVerified(recipientId, invite.getSenderId(), request.fingerprint(), request.signature());
        contactService.savePending(invite.getSenderId(), recipientId);

        invite.setAcceptedBy(recipientId);
        invite.setAcceptedAt(Instant.now());
        invite.setAcceptSeal(request.seal());
        invite.setDocumentId(documentId);
        // Kodla kilitli kopya artık gerekmez (Crypto_Design §7.6, alıcı adım 4).
        invite.setPayload(null);
        invite.setWrapped(null);
        invite.setSignature(null);
        return new InviteAcceptedResponse(documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InviteVerificationResponse> pendingVerifications(Long senderId) {
        return repository.findBySenderIdAndAcceptedAtIsNotNullAndCompletedAtIsNull(senderId).stream()
                .map(invite -> keyRepository.findById(invite.getAcceptedBy())
                        .map(key -> new InviteVerificationResponse(invite.getId(), invite.getSelfWrapped(),
                                invite.getVerifierHash(), invite.getAcceptSeal(), party(invite.getAcceptedBy(), key)))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional
    public void verify(Long senderId, Long inviteId, InviteVerifyRequest request) {
        Invite invite = awaitingVerification(senderId, inviteId);
        UserKey recipientKey = keyRepository.findById(invite.getAcceptedBy()).orElseThrow(InviteServiceImpl::notFound);
        if (!Fingerprints.of(recipientKey).equals(request.fingerprint())) {
            throw new BusinessException(ContactMessages.FINGERPRINT_MISMATCH);
        }
        contactService.saveVerified(senderId, invite.getAcceptedBy(), request.fingerprint(), request.signature());
        invite.setCompletedAt(Instant.now());
    }

    @Override
    @Transactional
    public void reject(Long senderId, Long inviteId) {
        Invite invite = awaitingVerification(senderId, inviteId);
        contactService.removePending(senderId, invite.getAcceptedBy());
        invite.setCompletedAt(Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SentInviteResponse> sent(Long senderId) {
        return repository.findBySenderIdAndAcceptedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(senderId, Instant.now())
                .stream()
                .map(i -> new SentInviteResponse(i.getId(), i.getRecipientPhone(), i.getKind(), i.getSizeBytes(),
                        i.getCreatedAt(), i.getExpiresAt()))
                .toList();
    }

    @Override
    @Transactional
    public void cancel(Long senderId, Long inviteId) {
        Invite invite = repository.findById(inviteId)
                .filter(i -> i.getSenderId().equals(senderId) && i.getAcceptedAt() == null)
                .orElseThrow(InviteServiceImpl::notFound);
        repository.delete(invite);
    }

    private Invite awaitingVerification(Long senderId, Long inviteId) {
        return repository.findById(inviteId)
                .filter(i -> i.getSenderId().equals(senderId) && i.getAcceptedAt() != null && i.getCompletedAt() == null)
                .orElseThrow(InviteServiceImpl::notFound);
    }

    private PartyResponse party(Long userId, UserKey key) {
        User user = userRepository.findById(userId).orElseThrow(InviteServiceImpl::notFound);
        return new PartyResponse(user.getId(), user.getFullName(), user.getPhone(), key.getEncryptionPublicKey(),
                key.getSigningPublicKey());
    }

    /** {@code SHA-256(doğrulayıcı)}, hex. Doğrulayıcı 32 bayt değilse boş. */
    static java.util.Optional<String> verifierHash(String verifierBase64) {
        try {
            byte[] verifier = Base64.getDecoder().decode(verifierBase64.getBytes(StandardCharsets.US_ASCII));
            if (verifier.length != 32) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(verifier)));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static byte[] decode(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(DocumentMessages.PAYLOAD_INVALID);
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException(GlobalMessages.RECORD_NOT_FOUND);
    }
}
