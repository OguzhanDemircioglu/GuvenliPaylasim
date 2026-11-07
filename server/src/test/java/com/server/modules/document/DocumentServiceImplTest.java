package com.server.modules.document;

import com.server.common.exception.BusinessException;
import com.server.common.exception.NotFoundException;
import com.server.modules.contact.ContactMessages;
import com.server.modules.contact.ContactService;
import com.server.modules.document.dto.DocumentContentResponse;
import com.server.modules.document.dto.DocumentSendRequest;
import com.server.modules.document.dto.DocumentSentResponse;
import com.server.modules.document.dto.RecipientKeyRequest;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Belge akışı ve gizlilik kuralları (Crypto_Design §4, §7.7, §8): her alıcıya ayrı sarmalı anahtar; sadece
 * doğrulanmış kişilere; her kutunun ömrü vardır; silinen kutudan iz kalmaz; "açılınca sil" onayla silinir.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    private static final Long SENDER = 1L;
    private static final Long AYSE = 2L;
    private static final Long ZEYNEP = 3L;
    private static final Long DOCUMENT_ID = 100L;
    private static final byte[] CIPHERTEXT = "şifreli-içerik".getBytes(StandardCharsets.UTF_8);
    private static final String PAYLOAD = Base64.getEncoder().encodeToString(CIPHERTEXT);

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentRecipientRepository recipientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserKeyRepository keyRepository;
    @Mock
    private ContactService contactService;
    @Mock
    private SendQuota quota;

    private DocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentServiceImpl(documentRepository, recipientRepository, userRepository, keyRepository,
                contactService, quota);
    }

    // ---------- Gönderim ----------

    @Test
    void sendStoresCiphertextOneWrappedKeyPerRecipientAndTheChosenLifetime() {
        when(keyRepository.existsById(SENDER)).thenReturn(true);
        stubRecipients(user(AYSE), user(ZEYNEP));
        stubSave();

        DocumentSentResponse response = service.send(SENDER, request(24, true,
                new RecipientKeyRequest(AYSE, "wrapped-for-ayse"), new RecipientKeyRequest(ZEYNEP, "wrapped-for-zeynep")));

        assertThat(response).isEqualTo(new DocumentSentResponse(DOCUMENT_ID, 2));
        ArgumentCaptor<Document> document = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(document.capture());
        assertThat(document.getValue().getPayload()).isEqualTo(CIPHERTEXT);
        assertThat(document.getValue().isDeleteOnOpen()).isTrue();
        assertThat(Duration.between(document.getValue().getCreatedAt(), document.getValue().getExpiresAt()))
                .isEqualTo(Duration.ofHours(24));
        verify(quota).check(SENDER, CIPHERTEXT.length);

        ArgumentCaptor<DocumentRecipient> recipients = ArgumentCaptor.forClass(DocumentRecipient.class);
        verify(recipientRepository, times(2)).save(recipients.capture());
        assertThat(recipients.getAllValues())
                .extracting(DocumentRecipient::getRecipientId, DocumentRecipient::getWrappedKey)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(AYSE, "wrapped-for-ayse"),
                        org.assertj.core.groups.Tuple.tuple(ZEYNEP, "wrapped-for-zeynep"));
    }

    @Test
    void lifetimeMustBeOneOfTheFixedChoices() {
        for (int hours : new int[]{0, 2, 48, 721}) {
            assertThatThrownBy(() -> service.send(SENDER, request(hours, false, new RecipientKeyRequest(AYSE, "w"))))
                    .hasMessage(DocumentMessages.LIFETIME_INVALID);
        }
        verify(documentRepository, never()).save(any());
    }

    @Test
    void senderWithoutKeysCannotSend() {
        when(keyRepository.existsById(SENDER)).thenReturn(false);

        assertThatThrownBy(() -> service.send(SENDER, request(168, false, new RecipientKeyRequest(AYSE, "wrapped"))))
                .hasMessage(DocumentMessages.SENDER_HAS_NO_KEYS);
    }

    @Test
    void duplicateSelfUnknownAndKeylessRecipientsAreRejected() {
        when(keyRepository.existsById(SENDER)).thenReturn(true);
        assertThatThrownBy(() -> service.send(SENDER, request(168, false,
                new RecipientKeyRequest(AYSE, "a"), new RecipientKeyRequest(AYSE, "b"))))
                .hasMessage(DocumentMessages.RECIPIENT_DUPLICATE);

        stubRecipients(user(SENDER));
        assertRecipientInvalid(new RecipientKeyRequest(SENDER, "wrapped"));

        when(userRepository.findAllById(Set.of(99L))).thenReturn(List.of());
        when(keyRepository.findAllById(Set.of(99L))).thenReturn(List.of());
        assertRecipientInvalid(new RecipientKeyRequest(99L, "wrapped"));

        when(userRepository.findAllById(Set.of(AYSE))).thenReturn(List.of(user(AYSE)));
        when(keyRepository.findAllById(Set.of(AYSE))).thenReturn(List.of());
        assertRecipientInvalid(new RecipientKeyRequest(AYSE, "wrapped"));
    }

    @Test
    void recipientMustBeAVerifiedContactWithUnchangedKey() {
        when(keyRepository.existsById(SENDER)).thenReturn(true);
        stubRecipients(user(AYSE));
        doThrow(new BusinessException(ContactMessages.NOT_VERIFIED))
                .when(contactService).requireVerifiedRecipients(SENDER, List.of(AYSE));

        assertThatThrownBy(() -> service.send(SENDER, request(168, false, new RecipientKeyRequest(AYSE, "wrapped"))))
                .hasMessage(ContactMessages.NOT_VERIFIED);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void quotaIsCheckedBeforeAnythingIsStored() {
        when(keyRepository.existsById(SENDER)).thenReturn(true);
        stubRecipients(user(AYSE));
        doThrow(new BusinessException(DocumentMessages.QUOTA_EXCEEDED)).when(quota).check(SENDER, CIPHERTEXT.length);

        assertThatThrownBy(() -> service.send(SENDER, request(168, false, new RecipientKeyRequest(AYSE, "wrapped"))))
                .hasMessage(DocumentMessages.QUOTA_EXCEEDED);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void payloadMustBeBase64() {
        when(keyRepository.existsById(SENDER)).thenReturn(true);
        stubRecipients(user(AYSE));

        DocumentSendRequest request = new DocumentSendRequest(DocumentKind.TEXT, "not base64!", "signature",
                List.of(new RecipientKeyRequest(AYSE, "wrapped")), 168, false);
        assertThatThrownBy(() -> service.send(SENDER, request)).hasMessage(DocumentMessages.PAYLOAD_INVALID);
    }

    @Test
    void acceptedInviteKeepsTheLifetimeChosenAtSendTime() {
        stubSave();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(30));

        Long id = service.createFromInvite(SENDER, AYSE, DocumentKind.TEXT, CIPHERTEXT, "signature",
                "wrapped-for-ayse", expiresAt, true);

        assertThat(id).isEqualTo(DOCUMENT_ID);
        ArgumentCaptor<Document> document = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(document.capture());
        assertThat(document.getValue().getExpiresAt()).isEqualTo(expiresAt);
        assertThat(document.getValue().isDeleteOnOpen()).isTrue();
        ArgumentCaptor<DocumentRecipient> recipient = ArgumentCaptor.forClass(DocumentRecipient.class);
        verify(recipientRepository).save(recipient.capture());
        assertThat(recipient.getValue().getRecipientId()).isEqualTo(AYSE);
    }

    // ---------- Açma ----------

    @Test
    void openReturnsCiphertextWithOwnWrappedKeyAndMarksRead() {
        DocumentRecipient recipient = recipient(AYSE, "wrapped-for-ayse");
        when(recipientRepository.findByDocumentIdAndRecipientId(DOCUMENT_ID, AYSE)).thenReturn(Optional.of(recipient));
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document(false)));
        when(userRepository.findById(SENDER)).thenReturn(Optional.of(user(SENDER)));
        UserKey senderKey = new UserKey();
        senderKey.setSigningPublicKey("sender-signing-key");
        when(keyRepository.findById(SENDER)).thenReturn(Optional.of(senderKey));

        DocumentContentResponse content = service.open(DOCUMENT_ID, AYSE);

        assertThat(content.payload()).isEqualTo(PAYLOAD);
        assertThat(content.wrappedKey()).isEqualTo("wrapped-for-ayse");
        assertThat(content.senderSigningPublicKey()).isEqualTo("sender-signing-key");
        assertThat(recipient.getReadAt()).isNotNull();
    }

    @Test
    void expiredDocumentLooksLikeItDoesNotExist() {
        when(recipientRepository.findByDocumentIdAndRecipientId(DOCUMENT_ID, AYSE))
                .thenReturn(Optional.of(recipient(AYSE, "w")));
        Document expired = document(false);
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.open(DOCUMENT_ID, AYSE)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.opened(DOCUMENT_ID, AYSE)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void nonRecipientCannotOpenConfirmOrDelete() {
        when(recipientRepository.findByDocumentIdAndRecipientId(DOCUMENT_ID, SENDER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(DOCUMENT_ID, SENDER)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.opened(DOCUMENT_ID, SENDER)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(DOCUMENT_ID, SENDER)).isInstanceOf(NotFoundException.class);
    }

    // ---------- Açılınca sil ve silme ----------

    @Test
    void openedConfirmationDeletesOnlyDeleteOnOpenBoxes() {
        DocumentRecipient recipient = recipient(AYSE, "w");
        when(recipientRepository.findByDocumentIdAndRecipientId(DOCUMENT_ID, AYSE)).thenReturn(Optional.of(recipient));

        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document(false)));
        service.opened(DOCUMENT_ID, AYSE);
        verify(recipientRepository, never()).delete(any());

        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document(true)));
        when(recipientRepository.existsByDocumentId(DOCUMENT_ID)).thenReturn(false);
        service.opened(DOCUMENT_ID, AYSE);
        verify(recipientRepository).delete(recipient);
        verify(documentRepository).deleteById(DOCUMENT_ID);
    }

    @Test
    void deleteRemovesTheRowAndKeepsTheDocumentForOtherRecipients() {
        DocumentRecipient recipient = recipient(AYSE, "w");
        when(recipientRepository.findByDocumentIdAndRecipientId(DOCUMENT_ID, AYSE)).thenReturn(Optional.of(recipient));
        when(recipientRepository.existsByDocumentId(DOCUMENT_ID)).thenReturn(true);

        service.delete(DOCUMENT_ID, AYSE);

        verify(recipientRepository).delete(recipient);
        verify(documentRepository, never()).deleteById(anyLong());
    }

    @Test
    void lastRecipientsDeleteRemovesTheWholeDocumentWithoutATrace() {
        DocumentRecipient recipient = recipient(ZEYNEP, "w");
        when(recipientRepository.findByDocumentIdAndRecipientId(DOCUMENT_ID, ZEYNEP)).thenReturn(Optional.of(recipient));
        when(recipientRepository.existsByDocumentId(DOCUMENT_ID)).thenReturn(false);

        service.delete(DOCUMENT_ID, ZEYNEP);

        verify(recipientRepository).delete(recipient);
        verify(documentRepository).deleteById(DOCUMENT_ID);
    }

    @Test
    void passwordResetDeletesEveryReceivedBox() {
        DocumentRecipient only = recipient(AYSE, "w1");
        DocumentRecipient shared = recipient(AYSE, "w2");
        shared.setDocumentId(DOCUMENT_ID + 1);
        when(recipientRepository.findByRecipientId(AYSE)).thenReturn(List.of(only, shared));
        when(recipientRepository.existsByDocumentId(DOCUMENT_ID)).thenReturn(false);
        when(recipientRepository.existsByDocumentId(DOCUMENT_ID + 1)).thenReturn(true);

        service.deleteAllReceived(AYSE);

        verify(recipientRepository).delete(only);
        verify(recipientRepository).delete(shared);
        verify(documentRepository).deleteById(DOCUMENT_ID);
        verify(documentRepository, never()).deleteById(DOCUMENT_ID + 1);
    }

    @Test
    void sentListShowsOnlyLiveBoxes() {
        when(documentRepository.findSent(any(), any())).thenReturn(List.of());

        assertThat(service.sent(SENDER)).isEmpty();
        verify(documentRepository).findSent(any(), any());
    }

    // ---------- Yardımcılar ----------

    private void assertRecipientInvalid(RecipientKeyRequest key) {
        assertThatThrownBy(() -> service.send(SENDER, request(168, false, key)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DocumentMessages.RECIPIENT_INVALID);
        verify(documentRepository, never()).save(any());
    }

    private void stubRecipients(User... users) {
        Set<Long> ids = Arrays.stream(users).map(User::getId).collect(Collectors.toSet());
        when(userRepository.findAllById(ids)).thenReturn(List.of(users));
        when(keyRepository.findAllById(ids)).thenReturn(Arrays.stream(users).map(user -> {
            UserKey key = new UserKey();
            key.setUserId(user.getId());
            return key;
        }).toList());
    }

    private void stubSave() {
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setId(DOCUMENT_ID);
            return document;
        });
    }

    private static DocumentSendRequest request(int lifetimeHours, boolean deleteOnOpen,
                                               RecipientKeyRequest... recipients) {
        return new DocumentSendRequest(DocumentKind.FILE, PAYLOAD, "signature", List.of(recipients), lifetimeHours,
                deleteOnOpen);
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setPhone("+90555000000" + id);
        user.setFullName("User " + id);
        return user;
    }

    private static Document document(boolean deleteOnOpen) {
        Document document = new Document();
        document.setId(DOCUMENT_ID);
        document.setSenderId(SENDER);
        document.setKind(DocumentKind.FILE);
        document.setPayload(CIPHERTEXT);
        document.setSignature("signature");
        document.setSizeBytes(CIPHERTEXT.length);
        document.setCreatedAt(Instant.now());
        document.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        document.setDeleteOnOpen(deleteOnOpen);
        return document;
    }

    private static DocumentRecipient recipient(Long recipientId, String wrappedKey) {
        DocumentRecipient recipient = new DocumentRecipient();
        recipient.setDocumentId(DOCUMENT_ID);
        recipient.setRecipientId(recipientId);
        recipient.setWrappedKey(wrappedKey);
        return recipient;
    }
}
