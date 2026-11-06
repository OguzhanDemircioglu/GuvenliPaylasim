package com.server.modules.verification;

/** Telefon doğrulama kodunun ne için üretildiği. Bir amaçla üretilen kod öteki için geçerli değildir. */
public enum VerificationPurpose {
    /** Kayıt: kod sadece kayıtlı olmayan numaraya gönderilir. */
    REGISTER,
    /** Parola sıfırlama: kod sadece kayıtlı numaraya gönderilir. */
    PASSWORD_RESET
}
