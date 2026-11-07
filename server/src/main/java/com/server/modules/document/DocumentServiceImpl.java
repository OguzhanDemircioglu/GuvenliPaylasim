package com.server.modules.document;

import com.server.common.exception.BusinessException;
import com.server.common.exception.NotFoundException;
import com.server.common.message.GlobalMessages;
import com.server.modules.contact.ContactService;
import com.server.modules.document.dto.DocumentContentResponse;
import com.server.modules.document.dto.DocumentSendRequest;
import com.server.modules.document.dto.DocumentSentResponse;
import com.server.modules.document.dto.InboxItemResponse;
import com.server.modules.document.dto.RecipientKeyRequest;
import com.server.modules.document.dto.SentDocumentRow;
import com.server.modules.document.dto.SentItemResponse;
import com.server.modules.document.dto.SentRecipientResponse;
import com.server.modules.key.UserKey;
import com.server.modules.key.UserKeyRepository;
import com.server.modules.user.User;
import com.server.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final UserKeyRepository keyRepository;
    private final ContactService contactService;
    private final SendQuota quota;

    @Override
    @Transactional
    public DocumentSentResponse send(Long senderId, DocumentSendRequest request) {
        if (!BoxLifetimes.valid(request.lifetimeHours())) {
            throw new BusinessException(DocumentMessages.LIFETIME_INVALID);
        }
        if (!keyRepository.existsById(senderId)) {
            throw new BusinessException(DocumentMessages.SENDER_HAS_NO_KEYS);
        }
        List<User> recipients = validRecipients(senderId, request.recipients());
        contactService.requireVerifiedRecipients(senderId, recipients.stream().map(User::getId).toList());
        byte[] payload = decode(request.payload());
        quota.check(senderId, payload.length);

        Instant now = Instant.now();
        Document document = newDocument(senderId, request.kind(), payload, request.signature(),
                now.plus(Duration.ofHours(request.lifetimeHours())), request.deleteOnOpen(), now);
        for (RecipientKeyRequest key : request.recipients()) {
            saveRecipient(document.getId(), key.userId(), key.wrappedKey());
        }
        return new DocumentSentResponse(document.getId(), recipients.size());
    }

    @Override
    @Transactional
    public Long createFromInvite(Long senderId, Long recipientId, DocumentKind kind, byte[] payload, String signature,
                                 String wrappedKey, Instant expiresAt, boolean deleteOnOpen) {
        Document document = newDocument(senderId, kind, payload, signature, expiresAt, deleteOnOpen, Instant.now());
        saveRecipient(document.getId(), recipientId, wrappedKey);
        return document.getId();
    }

    @Override
    public List<InboxItemResponse> inbox(Long recipientId) {
        return recipientRepository.findInbox(recipientId, Instant.now());
    }

    @Override
    @Transactional
    public DocumentContentResponse open(Long documentId, Long recipientId) {
        DocumentRecipient recipient = findRecipient(documentId, recipientId);
        Document document = findLive(documentId);
        User sender = userRepository.findById(document.getSenderId()).orElseThrow(DocumentServiceImpl::notFound);
        String senderSigningKey = keyRepository.findById(sender.getId()).map(UserKey::getSigningPublicKey).orElse(null);

        if (recipient.getReadAt() == null) {
            recipient.setReadAt(Instant.now());
        }
        return new DocumentContentResponse(document.getId(), document.getKind(),
                Base64.getEncoder().encodeToString(document.getPayload()), document.getSignature(),
                recipient.getWrappedKey(), sender.getId(), sender.getFullName(),
                senderSigningKey, document.getCreatedAt(), document.getExpiresAt(), document.isDeleteOnOpen());
    }

    @Override
    @Transactional
    public void opened(Long documentId, Long recipientId) {
        DocumentRecipient recipient = findRecipient(documentId, recipientId);
        if (findLive(documentId).isDeleteOnOpen()) {
            deleteRecipient(recipient);
        }
    }

    @Override
    @Transactional
    public void delete(Long documentId, Long recipientId) {
        deleteRecipient(findRecipient(documentId, recipientId));
    }

    @Override
    @Transactional
    public void deleteAllReceived(Long recipientId) {
        for (DocumentRecipient recipient : recipientRepository.findByRecipientId(recipientId)) {
            deleteRecipient(recipient);
        }
    }

    /** Satır tamamen silinir; son alıcıysa belge de (Crypto_Design §8.5). Kalıcı denetim kaydı tutulmaz. */
    private void deleteRecipient(DocumentRecipient recipient) {
        Long documentId = recipient.getDocumentId();
        recipientRepository.delete(recipient);
        recipientRepository.flush();
        if (!recipientRepository.existsByDocumentId(documentId)) {
            documentRepository.deleteById(documentId);
        }
    }

    @Override
    public List<SentItemResponse> sent(Long senderId) {
        List<SentDocumentRow> documents = documentRepository.findSent(senderId, Instant.now());
        if (documents.isEmpty()) {
            return List.of();
        }
        Map<Long, List<SentRecipientResponse>> recipients = recipientRepository
                .findSentRecipients(documents.stream().map(SentDocumentRow::id).toList()).stream()
                .collect(Collectors.groupingBy(SentRecipientResponse::documentId));
        return documents.stream()
                .map(d -> new SentItemResponse(d.id(), d.kind(), d.sizeBytes(), d.createdAt(), d.expiresAt(),
                        d.deleteOnOpen(), recipients.getOrDefault(d.id(), List.of())))
                .toList();
    }

    private Document newDocument(Long senderId, DocumentKind kind, byte[] payload, String signature, Instant expiresAt,
                                 boolean deleteOnOpen, Instant now) {
        Document document = new Document();
        document.setSenderId(senderId);
        document.setKind(kind);
        document.setPayload(payload);
        document.setSignature(signature);
        document.setSizeBytes(payload.length);
        document.setCreatedAt(now);
        document.setExpiresAt(expiresAt);
        document.setDeleteOnOpen(deleteOnOpen);
        return documentRepository.save(document);
    }

    private void saveRecipient(Long documentId, Long recipientId, String wrappedKey) {
        DocumentRecipient recipient = new DocumentRecipient();
        recipient.setDocumentId(documentId);
        recipient.setRecipientId(recipientId);
        recipient.setWrappedKey(wrappedKey);
        recipientRepository.save(recipient);
    }

    /** Ömrü dolmuş ama temizlik işinin henüz silmediği belge de yokmuş gibi davranır. */
    private Document findLive(Long documentId) {
        return documentRepository.findById(documentId)
                .filter(d -> d.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(DocumentServiceImpl::notFound);
    }

    /** Alıcılar tekrarsız, kayıtlı, gönderenden farklı ve anahtar sahibi olmalıdır. */
    private List<User> validRecipients(Long senderId, List<RecipientKeyRequest> keys) {
        Set<Long> ids = keys.stream().map(RecipientKeyRequest::userId).collect(Collectors.toSet());
        if (ids.size() != keys.size()) {
            throw new BusinessException(DocumentMessages.RECIPIENT_DUPLICATE);
        }
        Map<Long, User> users = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<Long> withKeys = keyRepository.findAllById(ids).stream().map(UserKey::getUserId).collect(Collectors.toSet());
        for (Long id : ids) {
            User user = users.get(id);
            if (user == null || id.equals(senderId) || !withKeys.contains(id)) {
                throw new BusinessException(DocumentMessages.RECIPIENT_INVALID);
            }
        }
        return List.copyOf(users.values());
    }

    private DocumentRecipient findRecipient(Long documentId, Long recipientId) {
        return recipientRepository.findByDocumentIdAndRecipientId(documentId, recipientId)
                .orElseThrow(DocumentServiceImpl::notFound);
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
