package com.desktop.crypto;

/** Kriptografik bir işlem başarısız oldu. Mesaj kullanıcıya gösterilebilecek Türkçe bir metindir. */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
