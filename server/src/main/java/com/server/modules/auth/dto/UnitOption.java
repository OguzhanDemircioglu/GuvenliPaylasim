package com.server.modules.auth.dto;

/** Kayıt formundaki birim listesi: oturumsuz istekte sadece ad ve kimlik gösterilir. */
public record UnitOption(Long id, String name) {
}
