package com.desktop.service;

import com.desktop.api.ApiClient;
import com.desktop.api.ApiException;
import com.desktop.api.Dto;
import com.desktop.crypto.PasswordKeys;
import com.desktop.crypto.UserKeys;
import tools.jackson.core.type.TypeReference;

import java.util.List;

/**
 * Giriş, kayıt ve parola akışları (docs/Crypto_Design.md §2–3, v2: §7.2–7.5).
 * <ol>
 *   <li>Paroladan, telefon tuzuyla authSecret ve kek türetilir; sunucuya sadece authSecret gider.</li>
 *   <li>Kayıtta ve parola sıfırlamada anahtarlar bu bilgisayarda üretilir ve kek ile şifrelenip hesapla birlikte
 *       gönderilir.</li>
 *   <li>Girişte sunucudaki şifreli paket kek ile bu bilgisayarda açılır.</li>
 * </ol>
 * Telefon parametreleri kullanıcının yazdığı haliyle gelir ve {@link Phones#normalize} ile denetlenir.
 */
public final class AuthService {

    private static final TypeReference<Object> ANY = new TypeReference<>() {
    };

    private final String serverUrl;

    public AuthService(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public Session login(String phone, char[] password) {
        return login(phone, password, null);
    }

    /**
     * {@code totpCode}: TOTP açık hesapta Authenticator kodu ya da kurtarma kodu. Kod gerekiyorsa ve verilmediyse
     * sunucu {@code auth_totp_required} döner; ekran kodu sorup bu metodu kodla tekrar çağırır.
     */
    public Session login(String phone, char[] password, String totpCode) {
        String normalized = requirePhone(phone);
        return login(normalized, PasswordKeys.derive(Phones.toE164(normalized), password), blankToNull(totpCode));
    }

    /** Telefona WhatsApp ile kayıt kodu gönderilir. Numara kayıtlıysa kod gitmez ama cevap aynıdır. */
    public void startRegistration(String phone) {
        new ApiClient(serverUrl).post("/api/auth/register/start", new Dto.PhoneRequest(requirePhone(phone)), ANY);
    }

    /** Anahtarlar burada üretilir ve hesapla aynı istekte kaydedilir; ardından normal giriş yapılır. */
    public Session completeRegistration(String phone, String code, String fullName, char[] password) {
        String normalized = requirePhone(phone);
        String e164 = Phones.toE164(normalized);
        PasswordKeys.Derived derived = PasswordKeys.derive(e164, password);
        UserKeys keys = UserKeys.generate();
        new ApiClient(serverUrl).post("/api/auth/register/complete",
                new Dto.RegisterRequest(normalized, code.trim(), fullName.trim(), derived.authSecret(),
                        bundle(keys, derived.kek(), e164)), ANY);
        return login(normalized, derived, null);
    }

    /** Kayıtlı telefona WhatsApp ile sıfırlama kodu gönderilir. Numara kayıtlı değilse kod gitmez ama cevap aynıdır. */
    public void startPasswordReset(String phone) {
        new ApiClient(serverUrl).post("/api/auth/password-reset/start", new Dto.PhoneRequest(requirePhone(phone)), ANY);
    }

    /**
     * Yeni parola ve yeni anahtar çifti (Crypto_Design §7.5). Eski özel anahtarlar eski parolayla kilitli olduğu için
     * kullanıcıya gelmiş kutular sunucuda silinir; ardından yeni parolayla giriş yapılır.
     */
    public Session completePasswordReset(String phone, String code, char[] newPassword, String totpCode) {
        String normalized = requirePhone(phone);
        String e164 = Phones.toE164(normalized);
        PasswordKeys.Derived derived = PasswordKeys.derive(e164, newPassword);
        UserKeys keys = UserKeys.generate();
        new ApiClient(serverUrl).post("/api/auth/password-reset/complete",
                new Dto.PasswordResetRequest(normalized, code.trim(), derived.authSecret(),
                        bundle(keys, derived.kek(), e164), blankToNull(totpCode)), ANY);
        // Az önce kullanılan Authenticator kodu tekrar geçmez; kurtarma kodu da tek kullanımlıktır. Bu yüzden
        // sıfırlamadan sonraki giriş için kod yeniden istenebilir (auth_totp_required).
        return login(normalized, derived, null);
    }

    private Session login(String normalizedPhone, PasswordKeys.Derived derived, String totpCode) {
        ApiClient api = new ApiClient(serverUrl);
        Dto.SessionUser user = api.login(normalizedPhone, derived.authSecret(), totpCode).user();

        UserKeys keys = loadKeys(api, user.phone(), derived.kek());
        if (keys == null) {
            // v2'de anahtarlar kayıtta üretilir; anahtarsız hesap sadece sunucunun dev demo kullanıcılarıdır.
            keys = UserKeys.generate();
            api.post("/api/keys/me", bundle(keys, derived.kek(), user.phone()), ANY);
        }
        return new Session(api, user, keys);
    }

    /**
     * Özel anahtarlar yeni parolayla yeniden şifrelenir ve parola ile aynı istekte gönderilir; ikisi birlikte
     * kaydedilir. Açık anahtarlar değişmez, eski belgeler açılmaya devam eder.
     */
    public void changePassword(Session session, char[] currentPassword, char[] newPassword) {
        String e164 = session.user().phone();
        PasswordKeys.Derived current = PasswordKeys.derive(e164, currentPassword);
        PasswordKeys.Derived next = PasswordKeys.derive(e164, newPassword);
        session.api().put("/api/auth/password",
                new Dto.ChangePasswordRequest(current.authSecret(), next.authSecret(),
                        bundle(session.keys(), next.kek(), e164)), ANY);
    }

    // ---------- İki adımlı doğrulama (Crypto_Design §7.4) ----------

    public Dto.TotpSetup setupTotp(Session session) {
        return session.api().post("/api/auth/totp/setup", null, new TypeReference<>() {
        });
    }

    /** İlk kod doğrulanınca açılır; dönen kurtarma kodları sadece bir kez gösterilir. */
    public List<String> enableTotp(Session session, String code) {
        Dto.TotpEnabled enabled = session.api().post("/api/auth/totp/enable", new Dto.TotpCodeRequest(code.trim()),
                new TypeReference<>() {
                });
        session.totpEnabled(true);
        return enabled.recoveryCodes();
    }

    /** Kapatmak için mevcut parola (bu bilgisayarda authSecret'a çevrilir) ve Authenticator ya da kurtarma kodu. */
    public void disableTotp(Session session, char[] currentPassword, String code) {
        String secret = PasswordKeys.derive(session.user().phone(), currentPassword).authSecret();
        session.api().post("/api/auth/totp/disable", new Dto.TotpDisableRequest(secret, code.trim()), ANY);
        session.totpEnabled(false);
    }

    public void logout(Session session) {
        session.api().logout();
    }

    /**
     * Parola kuralları istemcide uygulanır; sunucu parolanın kendisini hiç görmez (Security.md S2: en az 12 karakter).
     * Yaygın parola listesi kontrolü henüz yok.
     */
    public static String validateNewPassword(char[] password, char[] repeat) {
        String value = new String(password);
        if (value.length() < 12) {
            return "Parola en az 12 karakter olmalıdır.";
        }
        if (!value.chars().anyMatch(Character::isDigit) || !value.chars().anyMatch(Character::isLetter)) {
            return "Parola hem harf hem rakam içermelidir.";
        }
        if (!value.equals(new String(repeat))) {
            return "Parolalar birbiriyle aynı değil.";
        }
        return null;
    }

    /** Ekranlarda kullanılır: geçersizse kullanıcıya gösterilecek mesaj, geçerliyse null. */
    public static String validatePhone(String phone) {
        return Phones.normalize(phone) == null
                ? "Cep telefonunu 10 hane olarak yazın: 5XXXXXXXXX."
                : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requirePhone(String phone) {
        String normalized = Phones.normalize(phone);
        if (normalized == null) {
            throw new IllegalArgumentException(validatePhone(phone));
        }
        return normalized;
    }

    private static UserKeys loadKeys(ApiClient api, String phoneE164, byte[] kek) {
        try {
            Dto.KeyBundle bundle = api.get("/api/keys/me", new TypeReference<>() {
            });
            return UserKeys.open(bundle.privateKeyBundle(), kek, phoneE164, bundle.encryptionPublicKey(),
                    bundle.signingPublicKey());
        } catch (ApiException e) {
            if (e.status() == 404) {
                return null;
            }
            throw e;
        }
    }

    private static Dto.KeyBundle bundle(UserKeys keys, byte[] kek, String phoneE164) {
        return new Dto.KeyBundle(keys.encryptionPublicKey(), keys.signingPublicKey(), keys.seal(kek, phoneE164));
    }
}
