package com.desktop.api;

/** Sunucunun döndürdüğü hata. {@link #getMessage()} kullanıcıya gösterilecek Türkçe metindir. */
public class ApiException extends RuntimeException {

    private final int status;
    private final String key;

    public ApiException(int status, String key) {
        super(Messages.text(key));
        this.status = status;
        this.key = key;
    }

    public int status() {
        return status;
    }

    public String key() {
        return key;
    }
}
