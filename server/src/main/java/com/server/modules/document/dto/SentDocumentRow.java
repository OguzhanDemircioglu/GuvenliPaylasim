package com.server.modules.document.dto;

import com.server.modules.document.DocumentKind;

import java.time.Instant;

/** Gönderilenler listesi için kutunun özeti; şifreli içerik (payload) okunmaz. */
public record SentDocumentRow(Long id, DocumentKind kind, long sizeBytes, Instant createdAt, Instant expiresAt,
                              boolean deleteOnOpen) {
}
