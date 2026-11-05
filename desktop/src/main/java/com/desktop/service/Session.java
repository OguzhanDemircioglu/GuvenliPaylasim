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
    private volatile UserKeys keys;

    Session(ApiClient api, Dto.SessionUser user, UserKeys keys) {
        this.api = api;
        this.user = user;
        this.keys = keys;
    }

    public ApiClient api() {
        return api;
    }

    public Dto.SessionUser user() {
        return user;
    }

    /** İlk girişte parola değiştirilene kadar null olabilir. */
    public UserKeys keys() {
        return keys;
    }

    void keys(UserKeys keys) {
        this.keys = keys;
    }

    public boolean hasKeys() {
        return keys != null;
    }
}
