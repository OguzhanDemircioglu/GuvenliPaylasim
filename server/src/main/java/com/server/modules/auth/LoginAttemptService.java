package com.server.modules.auth;

/**
 * Parola denemesi sınırı. Deneme, parola kontrol edilmeden önce sayılır: aynı anda gelen istekler de sırayla sayılır
 * ve sınırı aşan istek parolaya hiç bakılmadan reddedilir. Başarılı denemeden sonra {@link #clear} çağrılır.
 */
public interface LoginAttemptService {

    /** Denemeyi sayar; sınır aşıldıysa (ya da engel sürüyorsa) 429 fırlatır, doğru şifre de kabul edilmez. */
    void countAttempt(String attemptKey);

    /** Numara başına, bütün IP'lerden toplam deneme ({@code phoneMaxAttempts}); anahtarı {@link #phoneKey}. */
    void countPhoneAttempt(String phone);

    static String phoneKey(String phone) {
        return "phone_" + phone;
    }

    void clear(String attemptKey);
}
