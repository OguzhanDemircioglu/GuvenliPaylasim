package com.server.modules.verification;

public interface VerificationService {

    /**
     * Kod üretir ve uygunsa (kayıtta numara kayıtlı değilse, sıfırlamada kayıtlıysa) kuyruğa koyar. Numara kayıtlı olsun
     * olmasın aynı işler yapılır ve aynı cevap döner (Crypto_Design §7.2). Sınır aşılırsa 429.
     *
     * @param phone E.164
     */
    void start(String phone, VerificationPurpose purpose, String ip);

    /**
     * Kodu doğrular ve tüketir. Yanlışsa deneme sayılır (ayrı transaction'da kalıcı olur); hak biterse kod silinir.
     * Her başarısızlık aynı mesajla 422 döner.
     *
     * @param phone E.164
     */
    void verify(String phone, VerificationPurpose purpose, String code);
}
