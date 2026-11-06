package com.server.modules.document;

import com.server.common.exception.BusinessException;
import com.server.common.exception.NotFoundException;
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
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Belge akışı: sunucu şifreli içeriği opak taşır, anahtarı her alıcıya ayrı saklar, son alıcı silince içeriği yok eder.
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

    private DocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentServiceImpl(documentRepository, recipientRepository, userRepository, keyRepository);
    }

    @Test
    void sendStoresCiphertextAndOneWrappedKeyPerRecipient() {
        when(keyRepository.existsById(SENDER)).thenReturn(true);
        stubRecipients(user(AYSE), user(ZEYNEP));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setId(DOCUMENT_ID);
            return document;
        });

        DocumentSentResponse response = service.send(SENDER, request(
                new RecipientKeyRequest(AYSE, "wrapped-for-ayse"), new RecipientKeyRequest(ZEYNEP, "wrapped-for-zeynep")));

        assertThat(response).isEqualTo(new DocumentSentResponse(DOCUMENT_ID, 2));

        ArgumentCaptor<Document> document = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(document.capture());
        assertThat(document.getValue().getSenderId()).isEqualTo(SENDER);
        assertThat(document.getValue().getPayload()).isEqualTo(CIPHERTEXT);
        assertThat(document.getValue().getSizeBytes()).isEqualTo(CIPHERTEXT.length);
        assertThat(document.getValue().getSignature()).isEqualTo("signature");

        ArgumentCaptor<DocumentRecipient> recipients = ArgumentCaptor.forClass(DocumentRecipient.class);
        verify(recipientRepository, times(2)).save(recipients.capture());
        assertThat(recipients.getAllValues())
                .extracting(DocumentRecipient::getDocumentId, DocumentRecipient::getRecipientId, DocumentRecipient::getWrappedKey)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(DOCUMENT_ID, AYSE, "wrapped-for-ayse"),
                        org.assertj.core.groups.Tuple.tuple(DOCUMENT_ID, ZEYNEP, "wrapped-for-zeynep"));
    }

    @Test
    void senderWithoutKeysCannotSend() {
        when(keyRepository.existsById(SENDER)).thenReturn(false);

        assertThatThrownBy(() -> service.send(SENDER, request(new RecipientKeyRequest(AYSE, "wrapped"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DocumentMessages.SENDER_HAS_NO_KEYS);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void duplicateRecipientIsRejected() {
        when(keyRepository.existsById(SENDER)).thenReturn(true);

        assertThatThrownBy(() -> service.send(SENDER, request(
                new RecipientKeyRequest(AYSE, "a"), new RecipientKeyRequest(AYSE, "b"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DocumentMessages.RECIPIENT_DUPLICATE);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void senderCannotSendToSelf() {
        when(keyRepository.existsById(SENDER)).thenReturn(true);
        stubRecipients(user(SENDER));

        assertRecipientInvalid(new RecipientKeyRequest(SENDER, "wrapped"));
    }

    @Test
    void recipientWithoutKeysIsRejected() {
        when(keyRepository.existsById(SENDER)).thenReturn(true);
        when(userRepository.findAllById(Set.of(AYSE))).thenReturn(List.of(user(AYSE)));
        when(keyRepository.findAllById(Set.of(AYSE))).thenReturn(List.of());

        assertRecipientInvalid(new RecipientKeyRequest(AYSE, "wrapped"));
    }

    @Test
    void unknownRecipientIsRejected() {
        when(keyRepository.existsById(SENDER)).thenReturn(true);
        when(userRepository.findAllById(Set.of(99L))).thenReturn(List.of());
        when(keyRepository.findAllById(Set.of(99L))).thenReturn(List.of());
        assertRecipientInvalid(new RecipientKeyRequest(99L, "wrapped"));
    }

    @Test
    void payloadMustBeBase64() {
        when(keyRepository.existsById(SENDER)).thenReturn(true);
        stubRecipients(user(AYSE));

        DocumentSendRequest request = new DocumentSendRequest(DocumentKind.TEXT, "not base64!", "signature",
                List.of(new RecipientKeyRequest(AYSE, "wrapped")));

        assertThatThrownBy(() -> service.send(SENDER, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DocumentMessages.PAYLOAD_INVALID);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void openReturnsCiphertextWithOwnWrappedKeyAndSenderSigningKeyAndMarksRead() {
        DocumentRecipient recipient = recipient(AYSE, "wrapped-for-ayse");
        Document document = document();
        User sender = user(SENDER);
        UserKey senderKey = new UserKey();
        senderKey.setUserId(SENDER);
        senderKey.setSigningPublicKey("sender-signing-key");
        when(recipientRepository.findByDocumentIdAndRecipientIdAndDeletedAtIsNull(DOCUMENT_ID, AYSE))
                .thenReturn(Optional.of(recipient));
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(userRepository.findById(SENDER)).thenReturn(Optional.of(sender));
        when(keyRepository.findById(SENDER)).thenReturn(Optional.of(senderKey));

        DocumentContentResponse content = service.open(DOCUMENT_ID, AYSE);

        assertThat(content.payload()).isEqualTo(PAYLOAD);
        assertThat(content.wrappedKey()).isEqualTo("wrapped-for-ayse");
        assertThat(content.signature()).isEqualTo("signature");
        assertThat(content.senderSigningPublicKey()).isEqualTo("sender-signing-key");
        Instant firstRead = recipient.getReadAt();
        assertThat(firstRead).isNotNull();

        service.open(DOCUMENT_ID, AYSE);
        assertThat(recipient.getReadAt()).isEqualTo(firstRead);
    }

    @Test
    void nonRecipientCannotOpenOrDelete() {
        when(recipientRepository.findByDocumentIdAndRecipientIdAndDeletedAtIsNull(DOCUMENT_ID, SENDER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(DOCUMENT_ID, SENDER)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(DOCUMENT_ID, SENDER)).isInstanceOf(NotFoundException.class);
        verify(documentRepository, never()).findById(any());
    }

    @Test
    void deleteRemovesOwnWrappedKeyButKeepsCiphertextForOtherRecipients() {
        DocumentRecipient recipient = recipient(AYSE, "wrapped-for-ayse");
        when(recipientRepository.findByDocumentIdAndRecipientIdAndDeletedAtIsNull(DOCUMENT_ID, AYSE))
                .thenReturn(Optional.of(recipient));
        when(recipientRepository.existsByDocumentIdAndDeletedAtIsNull(DOCUMENT_ID)).thenReturn(true);

        service.delete(DOCUMENT_ID, AYSE);

        assertThat(recipient.getWrappedKey()).isNull();
        assertThat(recipient.getDeletedAt()).isNotNull();
        verify(documentRepository, never()).findById(any());
    }

    @Test
    void lastRecipientDeletePurgesCiphertextAndSignature() {
        DocumentRecipient recipient = recipient(ZEYNEP, "wrapped-for-zeynep");
        Document document = document();
        when(recipientRepository.findByDocumentIdAndRecipientIdAndDeletedAtIsNull(DOCUMENT_ID, ZEYNEP))
                .thenReturn(Optional.of(recipient));
        when(recipientRepository.existsByDocumentIdAndDeletedAtIsNull(DOCUMENT_ID)).thenReturn(false);
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));

        service.delete(DOCUMENT_ID, ZEYNEP);

        assertThat(recipient.getWrappedKey()).isNull();
        assertThat(document.getPayload()).isNull();
        assertThat(document.getSignature()).isNull();
        assertThat(document.getPurgedAt()).isNotNull();
        // Kim, kime, ne zaman bilgisi denetim için kalır.
        assertThat(document.getSenderId()).isEqualTo(SENDER);
    }

    @Test
    void passwordResetDeletesEveryReceivedBoxAndPurgesWhereLastRecipient() {
        DocumentRecipient onlyRecipient = recipient(AYSE, "wrapped-1");
        DocumentRecipient sharedRecipient = recipient(AYSE, "wrapped-2");
        sharedRecipient.setDocumentId(DOCUMENT_ID + 1);
        Document purged = document();
        when(recipientRepository.findByRecipientIdAndDeletedAtIsNull(AYSE))
                .thenReturn(List.of(onlyRecipient, sharedRecipient));
        when(recipientRepository.existsByDocumentIdAndDeletedAtIsNull(DOCUMENT_ID)).thenReturn(false);
        when(recipientRepository.existsByDocumentIdAndDeletedAtIsNull(DOCUMENT_ID + 1)).thenReturn(true);
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(purged));

        service.deleteAllReceived(AYSE);

        assertThat(onlyRecipient.getWrappedKey()).isNull();
        assertThat(sharedRecipient.getWrappedKey()).isNull();
        assertThat(onlyRecipient.getDeletedAt()).isNotNull();
        assertThat(sharedRecipient.getDeletedAt()).isNotNull();
        assertThat(purged.getPayload()).isNull();
        verify(documentRepository, never()).findById(DOCUMENT_ID + 1);
    }

    private void assertRecipientInvalid(RecipientKeyRequest key) {
        assertThatThrownBy(() -> service.send(SENDER, request(key)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DocumentMessages.RECIPIENT_INVALID);
        verify(documentRepository, never()).save(any());
    }

    private void stubRecipients(User... users) {
        Set<Long> ids = java.util.Arrays.stream(users).map(User::getId).collect(java.util.stream.Collectors.toSet());
        when(userRepository.findAllById(ids)).thenReturn(List.of(users));
        when(keyRepository.findAllById(ids)).thenReturn(java.util.Arrays.stream(users).map(user -> {
            UserKey key = new UserKey();
            key.setUserId(user.getId());
            return key;
        }).toList());
    }

    private static DocumentSendRequest request(RecipientKeyRequest... recipients) {
        return new DocumentSendRequest(DocumentKind.FILE, PAYLOAD, "signature", List.of(recipients));
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setPhone("+90555000000" + id);
        user.setFullName("User " + id);
        return user;
    }

    private static Document document() {
        Document document = new Document();
        document.setId(DOCUMENT_ID);
        document.setSenderId(SENDER);
        document.setKind(DocumentKind.FILE);
        document.setPayload(CIPHERTEXT);
        document.setSignature("signature");
        document.setSizeBytes(CIPHERTEXT.length);
        document.setCreatedAt(Instant.now());
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
