package com.server.modules.contact;

import com.server.common.exception.BusinessException;
import com.server.common.exception.NotFoundException;
import com.server.modules.key.Fingerprints;
import com.server.modules.key.UserKey;
import com.server.modules.key.UserKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Crypto_Design §7.7–7.8: kodsuz gönderim sadece doğrulanmış ve anahtarı değişmemiş kişilere. */
@ExtendWith(MockitoExtension.class)
class ContactServiceImplTest {

    private static final Long ALI = 1L;
    private static final Long AYSE = 2L;

    @Mock
    private ContactRepository repository;
    @Mock
    private UserKeyRepository keyRepository;

    private ContactServiceImpl service;
    private UserKey ayseKey;

    @BeforeEach
    void setUp() throws Exception {
        service = new ContactServiceImpl(repository, keyRepository);
        ayseKey = new UserKey();
        ayseKey.setUserId(AYSE);
        ayseKey.setEncryptionPublicKey(publicKey("X25519"));
        ayseKey.setSigningPublicKey(publicKey("Ed25519"));
    }

    @Test
    void verifiedContactWithUnchangedKeyCanReceive() {
        stub(contact(ContactStatus.VERIFIED, Fingerprints.of(ayseKey)));

        assertThatCode(() -> service.requireVerifiedRecipients(ALI, List.of(AYSE))).doesNotThrowAnyException();
    }

    @Test
    void changedKeyPendingContactAndStrangerCannotReceive() throws Exception {
        stub(contact(ContactStatus.VERIFIED, "0000 0000 0000 0000 0000 0000 0000 0000"));
        assertNotVerified();

        stub(contact(ContactStatus.PENDING, null));
        assertNotVerified();

        when(repository.findByOwnerIdAndContactIdIn(ALI, List.of(AYSE))).thenReturn(List.of());
        assertNotVerified();
    }

    @Test
    void savePendingClearsAnOldFingerprint() {
        Contact existing = contact(ContactStatus.VERIFIED, "OLD");
        when(repository.findByOwnerIdAndContactId(ALI, AYSE)).thenReturn(Optional.of(existing));

        service.savePending(ALI, AYSE);

        assertThat(existing.getStatus()).isEqualTo(ContactStatus.PENDING);
        assertThat(existing.getFingerprint()).isNull();
        assertThat(existing.getSignature()).isNull();
    }

    @Test
    void removePendingNeverTouchesAVerifiedContact() {
        when(repository.findByOwnerIdAndContactId(ALI, AYSE)).thenReturn(Optional.of(contact(ContactStatus.VERIFIED, "FP")));

        service.removePending(ALI, AYSE);

        verify(repository, never()).delete(any());
    }

    @Test
    void deleteEndsBothDirectionsAndUnknownContactIs404() {
        when(repository.deleteBothDirections(ALI, AYSE)).thenReturn(2);
        service.delete(ALI, AYSE);
        verify(repository).deleteBothDirections(ALI, AYSE);

        when(repository.deleteBothDirections(ALI, 99L)).thenReturn(0);
        assertThatThrownBy(() -> service.delete(ALI, 99L)).isInstanceOf(NotFoundException.class);
    }

    private void assertNotVerified() {
        assertThatThrownBy(() -> service.requireVerifiedRecipients(ALI, List.of(AYSE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ContactMessages.NOT_VERIFIED);
    }

    private void stub(Contact contact) {
        when(repository.findByOwnerIdAndContactIdIn(ALI, List.of(AYSE))).thenReturn(List.of(contact));
        when(keyRepository.findAllById(List.of(AYSE))).thenReturn(List.of(ayseKey));
    }

    private static Contact contact(ContactStatus status, String fingerprint) {
        Contact contact = new Contact();
        contact.setOwnerId(ALI);
        contact.setContactId(AYSE);
        contact.setStatus(status);
        contact.setFingerprint(fingerprint);
        contact.setCreatedAt(Instant.now());
        return contact;
    }

    private static String publicKey(String algorithm) throws Exception {
        return Base64.getEncoder().encodeToString(
                KeyPairGenerator.getInstance(algorithm).generateKeyPair().getPublic().getEncoded());
    }
}
