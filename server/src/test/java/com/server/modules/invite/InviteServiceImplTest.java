package com.server.modules.invite;

import com.server.common.exception.BusinessException;
import com.server.common.exception.LoginBlockedException;
import com.server.common.exception.NotFoundException;
import com.server.modules.auth.LoginAttempt;
import com.server.modules.auth.LoginAttemptRepository;
import com.server.modules.auth.LoginProtectionProperties;
import com.server.modules.contact.ContactMessages;
import com.server.modules.contact.ContactService;
import com.server.modules.document.DocumentKind;
import com.server.modules.document.DocumentService;
import com.server.modules.invite.dto.InviteAcceptRequest;
import com.server.modules.invite.dto.InviteCreateRequest;
import com.server.modules.invite.dto.InviteRedeemResponse;
import com.server.modules.invite.dto.InviteVerifyRequest;
import com.server.modules.key.Fingerprints;
import com.server.modules.key.UserKey;
import com.server.modules.key.UserKeyRepository;
import com.server.modules.user.User;
import com.server.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Crypto_Design §7.6 ve Security.md S3/S4: davet sadece kendi telefonuna bırakılmışsa açılır, yanlış kod sayılır,
 * sunucu kayıttaki parmak izini güncel anahtarla karşılaştırır, iki taraf da kişi listesine girer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InviteServiceImplTest {

    private static final Long ALI = 1L;
    private static final Long AYSE = 2L;
    private static final Long MALLORY = 3L;
    private static final String AYSE_PHONE = "+905550000002";
    private static final byte[] VERIFIER = new byte[32];
    private static final String VERIFIER_B64 = Base64.getEncoder().encodeToString(VERIFIER);

    @Mock
    private InviteRepository repository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserKeyRepository keyRepository;
    @Mock
    private ContactService contactService;
    @Mock
    private DocumentService documentService;
    @Mock
    private LoginAttemptRepository attemptRepository;

    private InviteServiceImpl service;
    private UserKey aliKey;
    private UserKey ayseKey;

    @BeforeEach
    void setUp() throws Exception {
        service = new InviteServiceImpl(repository, userRepository, keyRepository, contactService, documentService,
                attemptRepository, new LoginProtectionProperties(5, Duration.ofHours(1)));
        aliKey = key(ALI);
        ayseKey = key(AYSE);
        when(userRepository.findById(ALI)).thenReturn(Optional.of(user(ALI, "+905550000001", "Ali")));
        when(userRepository.findById(AYSE)).thenReturn(Optional.of(user(AYSE, AYSE_PHONE, "Ayşe")));
        when(userRepository.findById(MALLORY)).thenReturn(Optional.of(user(MALLORY, "+905550000003", "Mallory")));
        when(keyRepository.existsById(any())).thenReturn(true);
        when(keyRepository.findById(ALI)).thenReturn(Optional.of(aliKey));
        when(keyRepository.findById(AYSE)).thenReturn(Optional.of(ayseKey));
    }

    // ---------- Oluşturma ----------

    @Test
    void createNeverLooksUpWhetherTheNumberIsRegistered() {
        when(repository.save(any(Invite.class))).thenAnswer(i -> withId(i.getArgument(0), 10L));

        var response = service.create(ALI, request("5551112233", 24));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.expiresAt()).isBetween(Instant.now().plus(Duration.ofHours(23)), Instant.now().plus(Duration.ofHours(25)));
        verify(userRepository, never()).findByPhone(anyString());
        verify(userRepository, never()).existsByPhone(anyString());
        ArgumentCaptor<Invite> saved = ArgumentCaptor.forClass(Invite.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRecipientPhone()).isEqualTo("+905551112233");
        assertThat(saved.getValue().getVerifierHash()).isEqualTo(verifierHash());
    }

    @Test
    void ownNumberUnsupportedTtlAndDailyLimitAreRejected() {
        assertThatThrownBy(() -> service.create(ALI, request("5550000001", 24))).hasMessage(InviteMessages.OWN_PHONE);
        assertThatThrownBy(() -> service.create(ALI, request("5551112233", 200))).hasMessage(InviteMessages.TTL_INVALID);
        when(repository.countBySenderIdAndCreatedAtAfter(eq(ALI), any())).thenReturn(20L);
        assertThatThrownBy(() -> service.create(ALI, request("5551112233", 1))).hasMessage(InviteMessages.DAILY_LIMIT);
        verify(repository, never()).save(any());
    }

    // ---------- Kodu girme ----------

    @Test
    void redeemSearchesOnlyTheRecipientsOwnNumber() {
        Invite invite = pendingInvite();
        when(repository.findRedeemable(eq(AYSE_PHONE), eq(verifierHash()), any())).thenReturn(Optional.of(invite));

        InviteRedeemResponse response = service.redeem(AYSE, VERIFIER_B64);

        assertThat(response.sender().userId()).isEqualTo(ALI);
        assertThat(response.wrapped()).isEqualTo("wrapped");
        verify(attemptRepository).deleteById("invite_redeem_" + AYSE);

        // Aynı kodla başka bir kullanıcı: kendi numarasında arandığı için bulunamaz.
        assertThatThrownBy(() -> service.redeem(MALLORY, VERIFIER_B64)).hasMessage(InviteMessages.CODE_INVALID);
        verify(repository).findRedeemable(eq("+905550000003"), eq(verifierHash()), any());
    }

    @Test
    void wrongCodeIsCountedAndBlockedUserIsRejected() {
        assertThatThrownBy(() -> service.redeem(AYSE, VERIFIER_B64)).isInstanceOf(BusinessException.class)
                .hasMessage(InviteMessages.CODE_INVALID);
        verify(attemptRepository).recordFailure(eq("invite_redeem_" + AYSE), any(), any(), eq(5));

        assertThatThrownBy(() -> service.redeem(AYSE, "not-base64!")).hasMessage(InviteMessages.CODE_INVALID);

        LoginAttempt blocked = org.mockito.Mockito.mock(LoginAttempt.class);
        when(blocked.isBlocked(any())).thenReturn(true);
        when(attemptRepository.findById("invite_redeem_" + AYSE)).thenReturn(Optional.of(blocked));
        assertThatThrownBy(() -> service.redeem(AYSE, VERIFIER_B64)).isInstanceOf(LoginBlockedException.class)
                .hasMessage(InviteMessages.REDEEM_BLOCKED);
    }

    // ---------- Kabul ----------

    @Test
    void acceptMovesTheBoxToTheInboxAndAddsBothSidesAsContacts() {
        Invite invite = pendingInvite();
        when(repository.findById(10L)).thenReturn(Optional.of(invite));
        when(documentService.createFromInvite(ALI, AYSE, DocumentKind.TEXT, invite.getPayload(), "signature",
                "wrapped-for-ayse")).thenReturn(100L);

        var response = service.accept(AYSE, 10L, accept(Fingerprints.of(aliKey)));

        assertThat(response.documentId()).isEqualTo(100L);
        verify(contactService).saveVerified(AYSE, ALI, Fingerprints.of(aliKey), "ayse-signature");
        verify(contactService).savePending(ALI, AYSE);
        assertThat(invite.getAcceptedBy()).isEqualTo(AYSE);
        assertThat(invite.getAcceptSeal()).isEqualTo("seal");
        assertThat(invite.getPayload()).isNull();
        assertThat(invite.getWrapped()).isNull();
    }

    @Test
    void acceptOfSomeoneElsesInviteLooksLikeItDoesNotExist() {
        when(repository.findById(10L)).thenReturn(Optional.of(pendingInvite()));

        assertThatThrownBy(() -> service.accept(MALLORY, 10L, accept(Fingerprints.of(aliKey))))
                .isInstanceOf(NotFoundException.class);
        verify(documentService, never()).createFromInvite(any(), any(), any(), any(), any(), any());
    }

    @Test
    void acceptWithAFingerprintThatIsNotTheSendersCurrentKeyIsRejected() {
        when(repository.findById(10L)).thenReturn(Optional.of(pendingInvite()));

        assertThatThrownBy(() -> service.accept(AYSE, 10L, accept(Fingerprints.of(ayseKey))))
                .hasMessage(ContactMessages.FINGERPRINT_MISMATCH);
        verify(contactService, never()).saveVerified(any(), any(), any(), any());
    }

    // ---------- Gönderenin doğrulaması ----------

    @Test
    void verifyStoresTheRecipientsFingerprintOnlyIfItMatchesTheirCurrentKey() {
        Invite invite = acceptedInvite();
        when(repository.findById(10L)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> service.verify(ALI, 10L, new InviteVerifyRequest(Fingerprints.of(aliKey), "sig")))
                .hasMessage(ContactMessages.FINGERPRINT_MISMATCH);

        service.verify(ALI, 10L, new InviteVerifyRequest(Fingerprints.of(ayseKey), "ali-signature"));
        verify(contactService).saveVerified(ALI, AYSE, Fingerprints.of(ayseKey), "ali-signature");
        assertThat(invite.getCompletedAt()).isNotNull();
    }

    @Test
    void onlyTheSenderCanVerifyOrReject() {
        when(repository.findById(10L)).thenReturn(Optional.of(acceptedInvite()));

        assertThatThrownBy(() -> service.verify(MALLORY, 10L, new InviteVerifyRequest(Fingerprints.of(ayseKey), "s")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.reject(AYSE, 10L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectRemovesThePendingContact() {
        Invite invite = acceptedInvite();
        when(repository.findById(10L)).thenReturn(Optional.of(invite));

        service.reject(ALI, 10L);

        verify(contactService).removePending(ALI, AYSE);
        assertThat(invite.getCompletedAt()).isNotNull();
    }

    @Test
    void cancelOnlyForOwnNotYetAcceptedInvite() {
        Invite invite = pendingInvite();
        when(repository.findById(10L)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> service.cancel(AYSE, 10L)).isInstanceOf(NotFoundException.class);
        service.cancel(ALI, 10L);
        verify(repository).delete(invite);
    }

    // ---------- Yardımcılar ----------

    private static InviteCreateRequest request(String phone, int ttl) {
        return new InviteCreateRequest(phone, ttl, DocumentKind.TEXT, Base64.getEncoder().encodeToString(new byte[]{1, 2}),
                "signature", "wrapped", verifierHash(), "self-wrapped");
    }

    private static InviteAcceptRequest accept(String fingerprint) {
        return new InviteAcceptRequest("wrapped-for-ayse", "seal", fingerprint, "ayse-signature");
    }

    private static Invite pendingInvite() {
        Invite invite = new Invite();
        invite.setId(10L);
        invite.setSenderId(ALI);
        invite.setRecipientPhone(AYSE_PHONE);
        invite.setKind(DocumentKind.TEXT);
        invite.setPayload(new byte[]{1, 2, 3});
        invite.setSignature("signature");
        invite.setWrapped("wrapped");
        invite.setVerifierHash(verifierHash());
        invite.setSelfWrapped("self-wrapped");
        invite.setCreatedAt(Instant.now());
        invite.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        return invite;
    }

    private static Invite acceptedInvite() {
        Invite invite = pendingInvite();
        invite.setAcceptedBy(AYSE);
        invite.setAcceptedAt(Instant.now());
        invite.setAcceptSeal("seal");
        return invite;
    }

    private static String verifierHash() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(VERIFIER));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Invite withId(Invite invite, Long id) {
        invite.setId(id);
        return invite;
    }

    private static User user(Long id, String phone, String name) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setPhone(phone);
        user.setFullName(name);
        return user;
    }

    private static UserKey key(Long userId) throws Exception {
        UserKey key = new UserKey();
        key.setUserId(userId);
        key.setEncryptionPublicKey(Base64.getEncoder().encodeToString(
                KeyPairGenerator.getInstance("X25519").generateKeyPair().getPublic().getEncoded()));
        key.setSigningPublicKey(Base64.getEncoder().encodeToString(
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded()));
        return key;
    }
}
