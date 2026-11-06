package com.server.modules.verification;

public final class VerificationMessages {

    /** Başarılı cevap: numara kayıtlı olsun olmasın aynıdır. */
    public static final String CODE_SENT = "verification_code_sent";
    public static final String CODE_REQUIRED = "verification_code_required";
    /** Yanlış, süresi dolmuş, deneme hakkı bitmiş ya da hiç üretilmemiş kod: hepsi aynı cevabı alır. */
    public static final String CODE_INVALID = "verification_code_invalid";
    public static final String RATE_LIMITED = "verification_rate_limited";

    private VerificationMessages() {
    }
}
