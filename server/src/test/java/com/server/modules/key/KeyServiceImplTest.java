package com.server.modules.key;

import com.server.common.exception.BusinessException;
import com.server.common.exception.NotFoundException;
import com.server.modules.key.dto.KeyBundleRequest;
import com.server.modules.key.dto.KeyBundleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Anahtar dizini: anahtarlar bir kez oluşturulur, bozuk açık anahtar kaydedilmez, parola değişince açık anahtarlar
 * aynı kalıp sadece şifreli özel anahtar paketi değişir.
 */
@ExtendWith(MockitoExtension.class)
class KeyServiceImplTest {

    private static final Long USER_ID = 5L;

    @Mock
    private UserKeyRepository repository;

    private KeyServiceImpl service;
    private String encryptionKey;
    private String signingKey;

    @BeforeEach
    void setUp() throws Exception {
        service = new KeyServiceImpl(repository);
        encryptionKey = publicKey("X25519");
        signingKey = publicKey("Ed25519");
    }

    @Test
    void createStoresValidatedPublicKeysAndOpaqueBundle() {
        when(repository.existsById(USER_ID)).thenReturn(false);
        when(repository.save(any(UserKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KeyBundleResponse response = service.create(USER_ID, new KeyBundleRequest(encryptionKey, signingKey, "bundle"));

        assertThat(response).isNotNull();
        ArgumentCaptor<UserKey> saved = ArgumentCaptor.forClass(UserKey.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getEncryptionPublicKey()).isEqualTo(encryptionKey);
        assertThat(saved.getValue().getSigningPublicKey()).isEqualTo(signingKey);
        assertThat(saved.getValue().getPrivateKeyBundle()).isEqualTo("bundle");
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void keysAreCreatedOnlyOnce() {
        when(repository.existsById(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(USER_ID, new KeyBundleRequest(encryptionKey, signingKey, "bundle")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(KeyMessages.ALREADY_EXISTS);
        verify(repository, never()).save(any());
    }

    @Test
    void garbagePublicKeysAreRejected() {
        when(repository.existsById(USER_ID)).thenReturn(false);

        for (KeyBundleRequest request : List.of(
                new KeyBundleRequest("bm90LWEta2V5", signingKey, "bundle"),
                new KeyBundleRequest(encryptionKey, "%%% not base64 %%%", "bundle"),
                new KeyBundleRequest(signingKey, signingKey, "bundle"))) {
            assertThatThrownBy(() -> service.create(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(KeyMessages.PUBLIC_KEY_INVALID);
        }
        verify(repository, never()).save(any());
    }

    @Test
    void passwordChangeReplacesOnlyTheEncryptedBundle() {
        UserKey existing = key("old-bundle");
        when(repository.findById(USER_ID)).thenReturn(Optional.of(existing));

        service.saveOnPasswordChange(USER_ID, new KeyBundleRequest(encryptionKey, signingKey, "new-bundle"));

        assertThat(existing.getPrivateKeyBundle()).isEqualTo("new-bundle");
        assertThat(existing.getEncryptionPublicKey()).isEqualTo(encryptionKey);
        assertThat(existing.getUpdatedAt()).isNotNull();
    }

    @Test
    void passwordChangeCannotSwapPublicKeys() throws Exception {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(key("old-bundle")));
        String otherKey = publicKey("X25519");

        assertThatThrownBy(() -> service.saveOnPasswordChange(USER_ID,
                new KeyBundleRequest(otherKey, signingKey, "new-bundle")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(KeyMessages.PUBLIC_KEY_INVALID);
    }

    @Test
    void passwordChangeCreatesKeysWhenTheyWereReset() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        service.saveOnPasswordChange(USER_ID, new KeyBundleRequest(encryptionKey, signingKey, "bundle"));

        verify(repository).save(any(UserKey.class));
    }

    @Test
    void missingOwnKeysIsNotFound() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwn(USER_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(KeyMessages.NOT_FOUND);
    }

    @Test
    void resetReplacesBothPublicKeysAndTheBundle() throws Exception {
        String newEncryptionKey = publicKey("X25519");
        String newSigningKey = publicKey("Ed25519");

        service.replaceOnReset(USER_ID, new KeyBundleRequest(newEncryptionKey, newSigningKey, "new-bundle"));

        verify(repository).deleteById(USER_ID);
        ArgumentCaptor<UserKey> saved = ArgumentCaptor.forClass(UserKey.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEncryptionPublicKey()).isEqualTo(newEncryptionKey);
        assertThat(saved.getValue().getSigningPublicKey()).isEqualTo(newSigningKey);
        assertThat(saved.getValue().getPrivateKeyBundle()).isEqualTo("new-bundle");
    }

    @Test
    void resetWithBrokenPublicKeyKeepsOldKeys() {
        assertThatThrownBy(() -> service.replaceOnReset(USER_ID, new KeyBundleRequest("bozuk", signingKey, "b")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(KeyMessages.PUBLIC_KEY_INVALID);
        verify(repository, never()).deleteById(any());
    }

    private UserKey key(String bundle) {
        UserKey key = new UserKey();
        key.setUserId(USER_ID);
        key.setEncryptionPublicKey(encryptionKey);
        key.setSigningPublicKey(signingKey);
        key.setPrivateKeyBundle(bundle);
        return key;
    }

    private static String publicKey(String algorithm) throws Exception {
        return Base64.getEncoder().encodeToString(
                KeyPairGenerator.getInstance(algorithm).generateKeyPair().getPublic().getEncoded());
    }
}
