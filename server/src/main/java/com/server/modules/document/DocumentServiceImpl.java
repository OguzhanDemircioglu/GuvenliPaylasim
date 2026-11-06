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
import com.server.modules.document.dto.SentItemResponse;
import com.server.modules.document.dto.SentRecipientResponse;
import com.server.modules.key.UserKey;
import com.server.modules.key.UserKeyRepository;
import com.server.modules.user.User;
import com.server.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional
    public DocumentSentResponse send(Long senderId, DocumentSendRequest request) {
        if (!keyRepository.existsById(senderId)) {
            throw new BusinessException(DocumentMessages.SENDER_HAS_NO_KEYS);
        }
        List<User> recipients = validRecipients(senderId, request.recipients());
        contactService.requireVerifiedRecipients(senderId, recipients.stream().map(User::getId).toList());

        Document document = new Document();
        document.setSenderId(senderId);
        document.setKind(request.kind());
        document.setPayload(decode(request.payload()));
        document.setSignature(request.signature());
        document.setSizeBytes(document.getPayload().length);
        document.setCreatedAt(Instant.now());
        documentRepository.save(document);

        for (RecipientKeyRequest key : request.recipients()) {
            DocumentRecipient recipient = new DocumentRecipient();
            recipient.setDocumentId(document.getId());
            recipient.setRecipientId(key.userId());
            recipient.setWrappedKey(key.wrappedKey());
            recipientRepository.save(recipient);
        }
        return new DocumentSentResponse(document.getId(), recipients.size());
    }

    @Override
    @Transactional
    public Long createFromInvite(Long senderId, Long recipientId, DocumentKind kind, byte[] payload, String signature,
                                 String wrappedKey) {
        Document document = new Document();
        document.setSenderId(senderId);
        document.setKind(kind);
        document.setPayload(payload);
        document.setSignature(signature);
        document.setSizeBytes(payload.length);
        document.setCreatedAt(Instant.now());
        documentRepository.save(document);

        DocumentRecipient recipient = new DocumentRecipient();
        recipient.setDocumentId(document.getId());
        recipient.setRecipientId(recipientId);
        recipient.setWrappedKey(wrappedKey);
        recipientRepository.save(recipient);
        return document.getId();
    }

    @Override
    public List<InboxItemResponse> inbox(Long recipientId) {
        return recipientRepository.findInbox(recipientId);
    }

    @Override
    @Transactional
    public DocumentContentResponse open(Long documentId, Long recipientId) {
        DocumentRecipient recipient = findRecipient(documentId, recipientId);
        Document document = documentRepository.findById(documentId).orElseThrow(DocumentServiceImpl::notFound);
        User sender = userRepository.findById(document.getSenderId()).orElseThrow(DocumentServiceImpl::notFound);
        String senderSigningKey = keyRepository.findById(sender.getId()).map(UserKey::getSigningPublicKey).orElse(null);

        if (recipient.getReadAt() == null) {
            recipient.setReadAt(Instant.now());
        }
        return new DocumentContentResponse(document.getId(), document.getKind(),
                Base64.getEncoder().encodeToString(document.getPayload()), document.getSignature(),
                recipient.getWrappedKey(), sender.getId(), sender.getFullName(),
                senderSigningKey, document.getCreatedAt());
    }

    @Override
    @Transactional
    public void delete(Long documentId, Long recipientId) {
        deleteRecipient(findRecipient(documentId, recipientId), Instant.now());
    }

    @Override
    @Transactional
    public void deleteAllReceived(Long recipientId) {
        Instant now = Instant.now();
        for (DocumentRecipient recipient : recipientRepository.findByRecipientIdAndDeletedAtIsNull(recipientId)) {
            deleteRecipient(recipient, now);
        }
    }

    private void deleteRecipient(DocumentRecipient recipient, Instant now) {
        Long documentId = recipient.getDocumentId();
        recipient.setWrappedKey(null);
        recipient.setDeletedAt(now);
        recipientRepository.flush();

        if (!recipientRepository.existsByDocumentIdAndDeletedAtIsNull(documentId)) {
            Document document = documentRepository.findById(documentId).orElseThrow(DocumentServiceImpl::notFound);
            document.setPayload(null);
            document.setSignature(null);
            document.setPurgedAt(now);
        }
    }

    @Override
    public List<SentItemResponse> sent(Long senderId) {
        List<Document> documents = documentRepository.findBySenderIdOrderByCreatedAtDesc(senderId);
        if (documents.isEmpty()) {
            return List.of();
        }
        Map<Long, List<SentRecipientResponse>> recipients = recipientRepository
                .findSentRecipients(documents.stream().map(Document::getId).toList()).stream()
                .collect(Collectors.groupingBy(SentRecipientResponse::documentId));
        return documents.stream()
                .map(d -> new SentItemResponse(d.getId(), d.getKind(), d.getSizeBytes(), d.getCreatedAt(),
                        d.getPurgedAt(), recipients.getOrDefault(d.getId(), List.of())))
                .toList();
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
        return recipientRepository.findByDocumentIdAndRecipientIdAndDeletedAtIsNull(documentId, recipientId)
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
