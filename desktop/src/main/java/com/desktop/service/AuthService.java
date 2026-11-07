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
    private final RememberedLogin remembered;

    public AuthService(String serverUrl) {
        this(serverUrl, RememberedLogin.defaultLocation());
    }

    public AuthService(String serverUrl, RememberedLogin remembered) {
        this.serverUrl = serverUrl;
        this.remembered = remembered;
    }

    // ---------- Beni hatırla (Crypto_Design §7.4) ----------

    /**
     * Oturum bu bilgisayarda 30 gün hatırlanır: refresh token ve özel anahtarlar DPAPI ile şifreli dosyaya yazılır.
     * Özel anahtarlar ayrıca kayda özel rastgele bir anahtarla kilitlenir (UserKeys.seal).
     */
    public void remember(Session session) {
        String token = session.api().refreshToken();
        if (token == null) {
            throw new IllegalStateException("Oturum bilgisi alınamadı");
        }
        byte[] kek = new byte[32];
        new java.security.SecureRandom().nextBytes(kek);
        String phone = session.user().phone();
        remembered.save(new RememberedLogin.Data(serverUrl, phone, token,
                java.time.Instant.now().plus(RememberedLogin.VALIDITY).getEpochSecond(),
                java.util.Base64.getEncoder().encodeToString(kek), session.keys().seal(kek, phone),
                session.keys().encryptionPublicKey(), session.keys().signingPublicKey()));
        keepRemembered(session);
    }

    /** Girişte "Beni hatırla" seçilmediyse bu bilgisayardaki eski kayıt (başka hesabınki olabilir) silinir. */
    public void forget() {
        remembered.clear();
    }

    public boolean hasRemembered() {
        return remembered.exists();
    }

    /**
     * Hatırlanan oturumu parola sormadan açar. Kayıt yoksa ya da süresi dolduysa boş döner. Sunucu oturumu kabul
     * etmezse (çıkış yapılmış, parola değişmiş ya da sıfırlanmış) kayıt silinir ve hata fırlatılır; sunucuya
     * ulaşılamazsa kayıt kalır.
     */
    public java.util.Optional<Session> resume() {
        var data = remembered.load(serverUrl, java.time.Instant.now());
        if (data.isEmpty()) {
            return java.util.Optional.empty();
        }
        RememberedLogin.Data saved = data.get();
        ApiClient api = new ApiClient(serverUrl);
        Dto.SessionInfo info;
        try {
            info = api.resume(saved.refreshToken());
        } catch (ApiException e) {
            if (e.status() != 0) {
                remembered.clear();
            }
            throw e;
        }
        remembered.updateRefreshToken(api.refreshToken());
        if (!saved.phone().equals(info.user().phone())) {
            remembered.clear();
            api.logout();
            return java.util.Optional.empty();
        }
        UserKeys keys = UserKeys.open(saved.bundle(), java.util.Base64.getDecoder().decode(saved.kek()),
                saved.phone(), saved.encryptionPublicKey(), saved.signingPublicKey());
        Session session = new Session(api, info.user(), keys);
        keepRemembered(session);
        return java.util.Optional.of(session);
    }

    private void keepRemembered(Session session) {
        session.remembered(true);
        session.api().onRefreshed(() -> remembered.updateRefreshToken(session.api().refreshToken()));
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
    public java.util.Optional<String> startRegistration(String phone) {
        Dto.VerificationStart started = new ApiClient(serverUrl).post("/api/auth/register/start",
                new Dto.PhoneRequest(requirePhone(phone)), new TypeReference<>() {
                });
        return java.util.Optional.ofNullable(started == null ? null : started.testCode());
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

    /**
     * Hesabı ve ilgili her şeyi sunucudan kalıcı olarak siler (Crypto_Design §7.13). Parola bu bilgisayarda authSecret'a
     * çevrilir; iki adımlı doğrulama açıksa kod da gerekir. Bu bilgisayardaki "Bu cihaza güven" kaydı da silinir.
     */
    public void deleteAccount(Session session, char[] password, String totpCode) {
        String secret = PasswordKeys.derive(session.user().phone(), password).authSecret();
        session.api().post("/api/users/me/delete", new Dto.DeleteAccountRequest(secret, blankToNull(totpCode)), ANY);
        remembered.clear();
    }

    /** Ad soyad değişir; telefon değiştirilemez (parola telefonla türetilir, Crypto_Design §7.3). */
    public void updateName(Session session, String fullName) {
        String name = fullName.trim();
        session.api().put("/api/users/me/name", new Dto.UpdateNameRequest(name), ANY);
        session.fullName(name);
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

    /** Çıkış: sunucudaki oturum kapanır ve "Beni hatırla" kaydı silinir. */
    public void logout(Session session) {
        remembered.clear();
        session.api().logout();
    }

    /**
     * Parola kuralları istemcide uygulanır; sunucu parolanın kendisini hiç görmez (Security.md S2): 12–20 karakter,
     * büyük ve küçük harf, rakam, özel karakter; yaygın kelime/isim, klavye dizisi ve kişisel bilgi kontrolü
     * (PasswordStrength).
     *
     * @param personal parolada geçmemesi gereken ad soyad ve telefon
     */
    public static String validateNewPassword(char[] password, char[] repeat, String... personal) {
        String value = new String(password);
        String weak = PasswordStrength.check(value, personal);
        if (weak != null) {
            return weak;
        }
        if (!value.equals(new String(repeat))) {
            return "Parolalar birbiriyle aynı değil.";
        }
        return null;
    }

    /** Ekranlarda kullanılır: geçersizse kullanıcıya gösterilecek mesaj, geçerliyse null. */
    public static String validatePhone(String phone) {
        return Phones.normalize(phone) == null
                ? "Cep telefonunu (5xx) xxx xx xx biçiminde yazın."
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
