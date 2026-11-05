package com.desktop.api;

import java.util.Map;

/**
 * Sunucu metin göndermez, mesaj anahtarı gönderir (Backend_Architecture §6). Çeviri istemcide yapılır.
 */
public final class Messages {

    public static final String CONNECTION_FAILED = "connection_failed";

    private static final Map<String, String> TEXTS = Map.ofEntries(
            Map.entry(CONNECTION_FAILED, "Sunucuya bağlanılamadı. Ağ bağlantınızı kontrol edin."),
            Map.entry("unexpected_error", "Beklenmeyen bir hata oluştu."),
            Map.entry("invalid_request", "İstek geçersiz."),
            Map.entry("access_denied", "Bu işlem için yetkiniz yok."),
            Map.entry("unauthorized", "Oturumunuz sona erdi. Lütfen tekrar giriş yapın."),
            Map.entry("record_not_found", "Kayıt bulunamadı ya da silinmiş."),
            Map.entry("record_modified", "Kayıt bu arada başka biri tarafından değiştirildi. Listeyi yenileyin."),
            Map.entry("record_conflict", "Kayıt başka kayıtlarla çakışıyor."),

            Map.entry("auth_invalid_credentials", "E-posta ya da parola hatalı."),
            Map.entry("auth_user_inactive", "Hesabınız pasif durumda. Yöneticinize başvurun."),
            Map.entry("auth_session_expired", "Oturumunuz sona erdi. Lütfen tekrar giriş yapın."),
            Map.entry("auth_login_blocked", "Çok sayıda hatalı deneme yapıldı. Bir saat sonra tekrar deneyin."),
            Map.entry("auth_current_password_incorrect", "Mevcut parolanız hatalı."),

            Map.entry("user_email_exists", "Bu e-posta adresiyle kayıtlı bir kullanıcı var."),
            Map.entry("user_email_invalid", "E-posta adresi geçersiz."),
            Map.entry("user_email_immutable", "E-posta adresi değiştirilemez."),
            Map.entry("user_full_name_required", "Ad soyad zorunludur."),
            Map.entry("user_unit_required", "Kullanıcının birimi seçilmelidir."),
            Map.entry("user_unit_not_found", "Seçilen birim bulunamadı."),
            Map.entry("user_last_superadmin", "Sistemdeki son süper yönetici pasife alınamaz ya da silinemez."),
            Map.entry("user_not_manageable", "Bu kullanıcıyı yönetme yetkiniz yok."),
            Map.entry("user_role_not_assignable", "Bu rolü veremezsiniz."),

            Map.entry("unit_name_required", "Birim adı zorunludur."),
            Map.entry("unit_name_exists", "Bu adla bir birim zaten var."),
            Map.entry("unit_in_use", "Bu birimde kullanıcılar var; önce kullanıcıları başka birime taşıyın."),

            Map.entry("key_already_exists", "Anahtarlarınız zaten oluşturulmuş."),
            Map.entry("key_not_found", "Anahtarlarınız henüz oluşturulmamış."),
            Map.entry("key_public_key_invalid", "Açık anahtar geçersiz."),

            Map.entry("document_payload_too_large", "Belge çok büyük. En fazla 10 MB gönderilebilir."),
            Map.entry("document_recipients_required", "En az bir alıcı seçilmelidir."),
            Map.entry("document_recipient_invalid", "Seçilen alıcılardan biri artık belge alamıyor. Listeyi yenileyin."),
            Map.entry("document_sender_has_no_keys", "Belge göndermek için önce anahtarlarınız oluşturulmalı."));

    private Messages() {
    }

    public static String text(String key) {
        return TEXTS.getOrDefault(key, "İşlem tamamlanamadı (" + key + ").");
    }
}
