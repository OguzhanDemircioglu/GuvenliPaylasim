package com.server.modules.auth;

public interface LoginAttemptService {

    /** Engelliyse 429 fırlatır; engel süresince doğru şifre de kabul edilmez. */
    void ensureNotBlocked(String attemptKey);

    void recordFailure(String attemptKey);

    /** Numara başına, bütün IP'lerden toplam yanlış deneme ({@code phoneMaxAttempts}); anahtarı {@link #phoneKey}. */
    void recordPhoneFailure(String phone);

    static String phoneKey(String phone) {
        return "phone_" + phone;
    }

    void clear(String attemptKey);
}
