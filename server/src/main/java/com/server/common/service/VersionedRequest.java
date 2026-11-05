package com.server.common.service;

/**
 * Güncelleme isteklerinin uyguladığı interface. Frontend, düzenlediği kaydın version değerini geri gönderir;
 * DB'deki değerle uyuşmazsa kayıt bu arada değişmiştir (409 record_modified).
 * <p>
 * Record'larda alan şöyle tanımlanır: {@code @NotNull(message = GlobalMessages.VERSION_REQUIRED) Long version}
 */
public interface VersionedRequest {

    Long version();
}
