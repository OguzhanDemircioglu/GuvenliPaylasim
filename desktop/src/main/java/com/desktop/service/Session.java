package com.desktop.service;

import com.desktop.api.ApiClient;
import com.desktop.api.Dto;
import com.desktop.crypto.UserKeys;

/**
 * Açık oturum: giriş yapan kullanıcı ve bellekteki özel anahtarları. Özel anahtarlar diske yazılmaz;
 * uygulama kapanınca ya da çıkış yapılınca bellekten silinir.
 */
public final class Session {

    private final ApiClient api;
    private final Dto.SessionUser user;
    private final UserKeys keys;
    /** Profilden açılıp kapatılabildiği için oturum boyunca değişebilir. */
    private volatile boolean totpEnabled;

    Session(ApiClient api, Dto.SessionUser user, UserKeys keys) {
        this.api = api;
        this.user = user;
        this.keys = keys;
        this.totpEnabled = user.totpEnabled();
    }

    public boolean totpEnabled() {
        return totpEnabled;
    }

    void totpEnabled(boolean enabled) {
        this.totpEnabled = enabled;
    }

    public ApiClient api() {
        return api;
    }

    public Dto.SessionUser user() {
        return user;
    }

    /** v2'de her oturumun anahtarı vardır: kayıtta üretilir, girişte açılır. */
    public UserKeys keys() {
        return keys;
    }
}
