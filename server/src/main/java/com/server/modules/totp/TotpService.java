package com.server.modules.totp;

import com.server.modules.totp.dto.TotpEnabledResponse;
import com.server.modules.totp.dto.TotpSetupResponse;

/**
 * İsteğe bağlı iki adımlı doğrulama (Crypto_Design §7.4). Kullanıcı profilinden açar; açıksa girişte ve parola
 * sıfırlamada Authenticator kodu ya da tek kullanımlık kurtarma kodu istenir.
 */
public interface TotpService {

    /** Yeni anahtar üretir; ilk kod doğrulanana kadar beklemede durur. */
    TotpSetupResponse setup(Long userId);

    /** İlk kod doğrulanınca açılır ve 10 kurtarma kodu üretilir. */
    TotpEnabledResponse enable(Long userId, String code);

    /** Mevcut parola ve geçerli bir kod (ya da kurtarma kodu) ile kapatılır. */
    void disable(Long userId, String password, String code);

    /**
     * Authenticator kodu (6 hane) ya da kurtarma kodunu doğrular ve tüketir: aynı Authenticator kodu ikinci kez,
     * kurtarma kodu hiç tekrar kullanılamaz. Ayrı transaction'da çalışır; sonuç çağıranın hatasıyla geri alınmaz.
     */
    boolean verify(Long userId, String code);

    /**
     * Çağıranın transaction'ındaki yönetilen kullanıcı kaydı üzerinde doğrular (parola sıfırlama). Ayrı transaction
     * kullanılmaz; yoksa çağıranın aynı kayda yazdığı eski sürüm tüketilmiş kodu geri getirirdi.
     */
    boolean verifyWithin(com.server.modules.user.User user, String code);
}
