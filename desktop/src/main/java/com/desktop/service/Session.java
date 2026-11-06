package com.desktop.service;

import com.desktop.api.ApiClient;
import com.desktop.api.Dto;
import com.desktop.crypto.UserKeys;

/**
 * Açık oturum: giriş yapan kullanıcı ve bellekteki özel anahtarları. Özel anahtarlar diske sadece "Beni hatırla"
 * seçilince, Windows veri korumasıyla şifreli olarak yazılır (RememberedLogin).
 */
public final class Session {

    private final ApiClient api;
    private final Dto.SessionUser user;
    private final UserKeys keys;
    /** Profilden açılıp kapatılabildiği için oturum boyunca değişebilir. */
    private volatile boolean totpEnabled;
    /** Profilden değiştirilebildiği için oturum boyunca değişebilir; {@link #user()} girişteki adı tutar. */
    private volatile String fullName;

    Session(ApiClient api, Dto.SessionUser user, UserKeys keys) {
        this.api = api;
        this.user = user;
        this.keys = keys;
        this.totpEnabled = user.totpEnabled();
        this.fullName = user.fullName();
    }

    /** "Beni hatırla" ile açıldıysa uygulama kapanırken sunucudaki oturum kapatılmaz. */
    private volatile boolean remembered;

    public boolean remembered() {
        return remembered;
    }

    void remembered(boolean value) {
        this.remembered = value;
    }

    public String fullName() {
        return fullName;
    }

    void fullName(String value) {
        this.fullName = value;
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
