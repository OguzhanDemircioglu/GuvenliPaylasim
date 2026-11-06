package com.server.modules.contact;

import com.server.modules.contact.dto.ContactResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByOwnerIdAndContactId(Long ownerId, Long contactId);

    List<Contact> findByOwnerIdAndContactIdIn(Long ownerId, java.util.Collection<Long> contactIds);

    /** Sahibin kişileri ve kişilerin güncel açık anahtarları, ada göre. */
    @Query("""
            SELECT new com.server.modules.contact.dto.ContactResponse(
                       u.id, u.fullName, u.phone, c.status, c.fingerprint, c.signature,
                       k.encryptionPublicKey, k.signingPublicKey)
              FROM Contact c
              JOIN User u ON u.id = c.contactId
              LEFT JOIN UserKey k ON k.userId = c.contactId
             WHERE c.ownerId = :ownerId
             ORDER BY u.fullName""")
    List<ContactResponse> findAllWithKeys(Long ownerId);

    /** Kişilik iki yönde birden biter (Crypto_Design §7.7). */
    @Modifying
    @Query("""
            delete from Contact c
             where (c.ownerId = :a and c.contactId = :b) or (c.ownerId = :b and c.contactId = :a)""")
    int deleteBothDirections(Long a, Long b);
}
