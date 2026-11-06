package com.server.modules.contact;

/** Kişi kaydının durumu (Crypto_Design §7.6–7.8). */
public enum ContactStatus {
    /** Parmak izi davet koduyla doğrulandı ve kayıt sahibinin imzasıyla saklandı. Kodsuz gönderim yapılabilir. */
    VERIFIED,
    /** Karşı taraf kodu girdi; bu tarafın istemcisi kabul mührünü henüz doğrulamadı. Gönderim yapılamaz. */
    PENDING
}
