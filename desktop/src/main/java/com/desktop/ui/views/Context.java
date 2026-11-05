package com.desktop.ui.views;

import com.desktop.api.Dto;
import com.desktop.service.AdminService;
import com.desktop.service.AuthService;
import com.desktop.service.DocumentService;
import com.desktop.service.Session;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Ekranların ortak ihtiyaçları: oturum, servisler ve birim adları. */
final class Context {

    final Session session;
    final AuthService auth;
    final DocumentService documents;
    final AdminService admin;
    private final Map<Long, String> unitNames = new ConcurrentHashMap<>();
    /** Gelen kutusu değişince kenar çubuğundaki sayaç yenilenir. */
    Runnable onInboxChanged = () -> {
    };

    Context(Session session, AuthService auth) {
        this.session = session;
        this.auth = auth;
        this.documents = new DocumentService(session);
        this.admin = new AdminService(session);
    }

    void units(List<Dto.Unit> units) {
        unitNames.clear();
        units.forEach(u -> unitNames.put(u.id(), u.name()));
    }

    String unitName(Long unitId) {
        return unitId == null ? "Birimsiz" : unitNames.getOrDefault(unitId, "Birim #" + unitId);
    }
}
