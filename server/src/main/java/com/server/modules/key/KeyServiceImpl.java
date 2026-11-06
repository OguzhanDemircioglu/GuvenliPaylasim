package com.server.modules.key;

import com.server.common.exception.BusinessException;
import com.server.common.exception.NotFoundException;
import com.server.modules.key.dto.KeyBundleRequest;
import com.server.modules.key.dto.KeyBundleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeyServiceImpl implements KeyService {

    private final UserKeyRepository repository;

    @Override
    public KeyBundleResponse getOwn(Long userId) {
        return repository.findById(userId).map(KeyBundleResponse::from)
                .orElseThrow(() -> new NotFoundException(KeyMessages.NOT_FOUND));
    }

    @Override
    @Transactional
    public KeyBundleResponse create(Long userId, KeyBundleRequest request) {
        if (repository.existsById(userId)) {
            throw new BusinessException(KeyMessages.ALREADY_EXISTS);
        }
        return KeyBundleResponse.from(repository.save(newKey(userId, request)));
    }

    @Override
    @Transactional
    public void saveOnPasswordChange(Long userId, KeyBundleRequest request) {
        UserKey key = repository.findById(userId).orElse(null);
        if (key == null) {
            repository.save(newKey(userId, request));
            return;
        }
        if (!key.getEncryptionPublicKey().equals(request.encryptionPublicKey())
                || !key.getSigningPublicKey().equals(request.signingPublicKey())) {
            throw new BusinessException(KeyMessages.PUBLIC_KEY_INVALID);
        }
        key.setPrivateKeyBundle(request.privateKeyBundle());
        key.setUpdatedAt(Instant.now());
    }

    @Override
    @Transactional
    public void replaceOnReset(Long userId, KeyBundleRequest request) {
        UserKey replacement = newKey(userId, request);
        repository.deleteById(userId);
        repository.flush();
        repository.save(replacement);
    }

    private static UserKey newKey(Long userId, KeyBundleRequest request) {
        requireValidPublicKey("X25519", request.encryptionPublicKey());
        requireValidPublicKey("Ed25519", request.signingPublicKey());
        UserKey key = new UserKey();
        key.setUserId(userId);
        key.setEncryptionPublicKey(request.encryptionPublicKey());
        key.setSigningPublicKey(request.signingPublicKey());
        key.setPrivateKeyBundle(request.privateKeyBundle());
        key.setCreatedAt(Instant.now());
        return key;
    }

    /** Bozuk bir açık anahtar kaydedilirse o kişiye gönderilen her belge açılamaz; kaydetmeden önce çözülür. */
    private static void requireValidPublicKey(String algorithm, String base64) {
        try {
            KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new BusinessException(KeyMessages.PUBLIC_KEY_INVALID);
        }
    }
}
