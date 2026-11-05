package com.desktop.service;

import com.desktop.api.ApiClient;
import com.desktop.api.ApiException;
import com.desktop.api.Dto;
import com.desktop.crypto.PasswordKeys;
import com.desktop.crypto.UserKeys;
import tools.jackson.core.type.TypeReference;

import java.util.List;

/**
 * Giriş ve parola akışları (docs/Crypto_Design.md §2–3).
 * <ol>
 *   <li>Paroladan authSecret ve kek türetilir; sunucuya sadece authSecret gider.</li>
 *   <li>Kullanıcının anahtarları varsa sunucudaki şifreli paket kek ile bu bilgisayarda açılır.</li>
 *   <li>Yoksa (ilk giriş) anahtarlar bu bilgisayarda üretilir. İlk parola değiştirilecekse anahtarlar parola
 *       değişikliğiyle birlikte kaydedilir, değilse hemen kaydedilir.</li>
 * </ol>
 */
public final class AuthService {

    private final String serverUrl;

    public AuthService(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public Session login(String email, char[] password) {
        return login(email, PasswordKeys.derive(email, password));
    }

    /** Hesap açılır, ardından normal giriş yapılır; anahtarlar ilk girişte bu bilgisayarda üretilir. */
    public Session register(String fullName, String email, Long unitId, char[] password) {
        PasswordKeys.Derived derived = PasswordKeys.derive(email, password);
        new ApiClient(serverUrl).post("/api/auth/register",
                new Dto.RegisterRequest(fullName.trim(), email.trim(), derived.authSecret(), unitId, null),
                new TypeReference<Object>() {
                });
        return login(email, derived);
    }

    /** Kayıt formundaki birim listesi; oturum gerektirmez. */
    public List<Dto.Unit> units() {
        return new ApiClient(serverUrl).get("/api/auth/units", new TypeReference<>() {
        });
    }

    private Session login(String email, PasswordKeys.Derived derived) {
        ApiClient api = new ApiClient(serverUrl);
        Dto.SessionUser user = api.login(email.trim(), derived.authSecret()).user();

        UserKeys keys = loadKeys(api, user.email(), derived.kek());
        if (keys == null && !user.mustChangePassword()) {
            keys = UserKeys.generate();
            api.post("/api/keys/me", bundle(keys, derived.kek(), user.email()), new TypeReference<Object>() {
            });
        }
        return new Session(api, user, keys);
    }

    /**
     * Özel anahtarlar yeni parolayla yeniden şifrelenir ve parola ile aynı istekte gönderilir; ikisi birlikte
     * kaydedilir. Anahtar yoksa (ilk giriş ya da yönetici parolayı sıfırladıysa) burada üretilir.
     */
    public void changePassword(Session session, char[] currentPassword, char[] newPassword) {
        String email = session.user().email();
        PasswordKeys.Derived current = PasswordKeys.derive(email, currentPassword);
        PasswordKeys.Derived next = PasswordKeys.derive(email, newPassword);
        UserKeys keys = session.hasKeys() ? session.keys() : UserKeys.generate();

        session.api().put("/api/auth/password",
                new Dto.ChangePasswordRequest(current.authSecret(), next.authSecret(), bundle(keys, next.kek(), email)),
                new TypeReference<Object>() {
                });
        session.keys(keys);
    }

    public void logout(Session session) {
        session.api().logout();
    }

    /** Parola kuralları istemcide uygulanır; sunucu parolanın kendisini hiç görmez. */
    public static String validateNewPassword(char[] password, char[] repeat) {
        String value = new String(password);
        if (value.length() < 10) {
            return "Yeni parola en az 10 karakter olmalıdır.";
        }
        if (!value.chars().anyMatch(Character::isDigit) || !value.chars().anyMatch(Character::isLetter)) {
            return "Yeni parola hem harf hem rakam içermelidir.";
        }
        if (!value.equals(new String(repeat))) {
            return "Yeni parolalar birbiriyle aynı değil.";
        }
        return null;
    }

    private static UserKeys loadKeys(ApiClient api, String email, byte[] kek) {
        try {
            Dto.KeyBundle bundle = api.get("/api/keys/me", new TypeReference<>() {
            });
            return UserKeys.open(bundle.privateKeyBundle(), kek, email, bundle.encryptionPublicKey(),
                    bundle.signingPublicKey());
        } catch (ApiException e) {
            if (e.status() == 404) {
                return null;
            }
            throw e;
        }
    }

    private static Dto.KeyBundle bundle(UserKeys keys, byte[] kek, String email) {
        return new Dto.KeyBundle(keys.encryptionPublicKey(), keys.signingPublicKey(), keys.seal(kek, email));
    }
}
