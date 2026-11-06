package com.server.modules.contact;

import com.server.common.exception.BusinessException;
import com.server.common.exception.NotFoundException;
import com.server.common.message.GlobalMessages;
import com.server.modules.contact.dto.ContactResponse;
import com.server.modules.key.Fingerprints;
import com.server.modules.key.UserKey;
import com.server.modules.key.UserKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactServiceImpl implements ContactService {

    private final ContactRepository repository;
    private final UserKeyRepository keyRepository;

    @Override
    public List<ContactResponse> list(Long ownerId) {
        return repository.findAllWithKeys(ownerId);
    }

    @Override
    @Transactional
    public void delete(Long ownerId, Long contactId) {
        if (repository.deleteBothDirections(ownerId, contactId) == 0) {
            throw new NotFoundException(GlobalMessages.RECORD_NOT_FOUND);
        }
    }

    @Override
    public void requireVerifiedRecipients(Long senderId, Collection<Long> recipientIds) {
        Map<Long, Contact> contacts = repository.findByOwnerIdAndContactIdIn(senderId, recipientIds).stream()
                .collect(Collectors.toMap(Contact::getContactId, Function.identity()));
        Map<Long, UserKey> keys = keyRepository.findAllById(recipientIds).stream()
                .collect(Collectors.toMap(UserKey::getUserId, Function.identity()));
        for (Long id : recipientIds) {
            Contact contact = contacts.get(id);
            UserKey key = keys.get(id);
            if (contact == null || contact.getStatus() != ContactStatus.VERIFIED || key == null
                    || !Fingerprints.of(key).equals(contact.getFingerprint())) {
                throw new BusinessException(ContactMessages.NOT_VERIFIED);
            }
        }
    }

    @Override
    @Transactional
    public void saveVerified(Long ownerId, Long contactId, String fingerprint, String signature) {
        Contact contact = repository.findByOwnerIdAndContactId(ownerId, contactId).orElseGet(() -> newContact(ownerId, contactId));
        contact.setStatus(ContactStatus.VERIFIED);
        contact.setFingerprint(fingerprint);
        contact.setSignature(signature);
        contact.setUpdatedAt(Instant.now());
        repository.save(contact);
    }

    @Override
    @Transactional
    public void savePending(Long ownerId, Long contactId) {
        Contact contact = repository.findByOwnerIdAndContactId(ownerId, contactId).orElseGet(() -> newContact(ownerId, contactId));
        contact.setStatus(ContactStatus.PENDING);
        contact.setFingerprint(null);
        contact.setSignature(null);
        contact.setUpdatedAt(Instant.now());
        repository.save(contact);
    }

    @Override
    @Transactional
    public void removePending(Long ownerId, Long contactId) {
        repository.findByOwnerIdAndContactId(ownerId, contactId)
                .filter(contact -> contact.getStatus() == ContactStatus.PENDING)
                .ifPresent(repository::delete);
    }

    private static Contact newContact(Long ownerId, Long contactId) {
        Contact contact = new Contact();
        contact.setOwnerId(ownerId);
        contact.setContactId(contactId);
        contact.setCreatedAt(Instant.now());
        return contact;
    }
}
