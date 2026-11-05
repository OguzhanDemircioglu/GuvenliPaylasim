package com.server.modules.key;

import com.server.modules.key.dto.KeyBundleRequest;
import com.server.modules.key.dto.KeyBundleResponse;
import com.server.modules.key.dto.RecipientResponse;

import java.util.Collection;
import java.util.List;

public interface KeyService {

    /** Kullanıcının kendi anahtar paketi; yoksa 404 {@code key_not_found}. */
    KeyBundleResponse getOwn(Long userId);

    /** İlk girişte üretilen anahtarlar. Anahtarı olan kullanıcı tekrar oluşturamaz (422 {@code key_already_exists}). */
    KeyBundleResponse create(Long userId, KeyBundleRequest request);

    /**
     * Parola değişince özel anahtarlar yeni parolayla yeniden şifrelenir. Anahtar yoksa (ilk giriş) oluşturulur.
     * Açık anahtarlar değişmez; değişirse eski belgeler açılamaz hale gelirdi.
     */
    void saveOnPasswordChange(Long userId, KeyBundleRequest request);

    List<RecipientResponse> findRecipients(Collection<Long> unitIds, Long excludedUserId);
}
