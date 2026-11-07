package com.desktop.api;

import java.util.Map;

/**
 * Sunucu metin göndermez, mesaj anahtarı gönderir (Backend_Architecture §6). Çeviri istemcide yapılır.
 */
public final class Messages {

    public static final String CONNECTION_FAILED = "connection_failed";
    public static final String SERVER_IDENTITY_FAILED = "server_identity_failed";

    private static final Map<String, String> TEXTS = Map.ofEntries(
            Map.entry(CONNECTION_FAILED, "Sunucuya bağlanılamadı. Ağ bağlantınızı kontrol edin."),
            Map.entry(SERVER_IDENTITY_FAILED, "Sunucunun kimliği doğrulanamadı; bağlantı güvenli değil ve kesildi. "
                    + "Başka bir ağdan deneyin ya da yöneticinize bildirin."),
            Map.entry("unexpected_error", "Beklenmeyen bir hata oluştu."),
            Map.entry("invalid_request", "İstek geçersiz."),
            Map.entry("access_denied", "Bu işlem için yetkiniz yok."),
            Map.entry("unauthorized", "Oturumunuz sona erdi. Lütfen tekrar giriş yapın."),
            Map.entry("record_not_found", "Kayıt bulunamadı ya da silinmiş."),
            Map.entry("record_modified", "Kayıt bu arada başka biri tarafından değiştirildi. Listeyi yenileyin."),
            Map.entry("record_conflict", "Kayıt başka kayıtlarla çakışıyor."),

            Map.entry("auth_invalid_credentials", "Telefon ya da parola hatalı."),
            Map.entry("auth_phone_required", "Cep telefonu zorunludur."),
            Map.entry("auth_totp_required", "Authenticator kodunuzu girin."),
            Map.entry("auth_totp_invalid", "Authenticator kodu ya da kurtarma kodu hatalı."),
            Map.entry("totp_code_invalid", "Kod hatalı ya da daha önce kullanılmış. Uygulamadaki güncel kodu yazın."),
            Map.entry("totp_code_required", "Authenticator kodunu yazın."),
            Map.entry("totp_already_enabled", "İki adımlı doğrulama zaten açık."),
            Map.entry("totp_not_enabled", "İki adımlı doğrulama açık değil."),
            Map.entry("totp_setup_required", "Önce kurulumu başlatın."),
            Map.entry("totp_blocked", "Çok sayıda hatalı kod girildi. Bir saat sonra tekrar deneyin."),
            Map.entry("auth_phone_invalid", "Cep telefonunu (5xx) xxx xx xx biçiminde yazın."),
            Map.entry("auth_session_expired", "Oturumunuz sona erdi. Lütfen tekrar giriş yapın."),
            Map.entry("auth_login_blocked", "Çok sayıda hatalı deneme yapıldı. Bir saat sonra tekrar deneyin."),
            Map.entry("auth_current_password_incorrect", "Mevcut parolanız hatalı."),

            Map.entry("user_phone_exists", "Bu numarayla kayıtlı bir hesap var. Giriş yapın ya da parolanızı sıfırlayın."),
            Map.entry("user_phone_required", "Cep telefonu zorunludur."),
            Map.entry("user_phone_invalid", "Cep telefonunu (5xx) xxx xx xx biçiminde yazın."),
            Map.entry("user_full_name_required", "Ad soyad zorunludur."),
            Map.entry("user_full_name_too_long", "Ad soyad en fazla 30 karakter olabilir."),
            Map.entry("user_full_name_invalid", "Ad soyadda sadece harf, boşluk, kesme, nokta ve tire kullanılabilir."),
            Map.entry("user_full_name_words", "Adınızı ve soyadınızı yazın: en az iki, en fazla üç kelime."),

            Map.entry("verification_code_required", "WhatsApp'a gelen kodu yazın."),
            Map.entry("verification_code_invalid", "Kod hatalı ya da süresi dolmuş. Yeni kod isteyin."),
            Map.entry("verification_rate_limited", "Çok sık kod istendi. Bir süre sonra tekrar deneyin."),

            Map.entry("key_already_exists", "Anahtarlarınız zaten oluşturulmuş."),
            Map.entry("key_not_found", "Anahtarlarınız henüz oluşturulmamış."),
            Map.entry("key_public_key_invalid", "Açık anahtar geçersiz."),

            Map.entry("contact_not_verified", "Alıcı doğrulanmış kişiniz değil ya da anahtarı değişmiş. Davet koduyla gönderin."),
            Map.entry("contact_fingerprint_mismatch", "Anahtar bu arada değişmiş. İşlemi yeni bir davet koduyla tekrarlayın."),
            Map.entry("invite_code_invalid", "Kod hatalı, süresi dolmuş ya da bu numaraya gönderilmemiş."),
            Map.entry("invite_redeem_blocked", "Çok sayıda hatalı kod girildi. Bir saat sonra tekrar deneyin."),
            Map.entry("invite_own_phone", "Kendinize davet gönderemezsiniz."),
            Map.entry("invite_daily_limit", "Bugün için davet sınırına ulaştınız (günde 20)."),
            Map.entry("invite_ttl_invalid", "Geçerlilik süresi geçersiz."),
            Map.entry("invite_phone_invalid", "Alıcının cep telefonunu (5xx) xxx xx xx biçiminde yazın."),
            Map.entry("document_lifetime_invalid", "Kutunun ömrü geçersiz."),
            Map.entry("document_daily_limit", "Bugün için gönderim sınırına ulaştınız (günde 200 kutu)."),
            Map.entry("document_quota_exceeded", "Gönderdiğiniz ve henüz silinmemiş kutuların toplamı 500 MB'ı aşıyor. Bazılarının ömrü dolunca tekrar deneyin."),
            Map.entry("invite_ttl_exceeds_lifetime", "Kodun geçerlilik süresi kutunun ömründen uzun olamaz."),
            Map.entry("document_payload_too_large", "Belge çok büyük. En fazla 10 MB gönderilebilir."),
            Map.entry("document_recipients_required", "En az bir alıcı seçilmelidir."),
            Map.entry("document_recipient_invalid", "Seçilen alıcılardan biri artık belge alamıyor."),
            Map.entry("document_sender_has_no_keys", "Belge göndermek için önce anahtarlarınız oluşturulmalı."));

    private Messages() {
    }

    public static String text(String key) {
        return TEXTS.getOrDefault(key, "İşlem tamamlanamadı (" + key + ").");
    }
}
