package com.desktop.ui.views;

import com.desktop.service.AuthService;
import com.desktop.service.DocumentService;
import com.desktop.service.Session;

/** Ekranların ortak ihtiyaçları: oturum ve servisler. */
final class Context {

    final Session session;
    final AuthService auth;
    final DocumentService documents;
    /** Gelen kutusu değişince kenar çubuğundaki sayaç yenilenir. */
    Runnable onInboxChanged = () -> {
    };

    Context(Session session, AuthService auth) {
        this.session = session;
        this.auth = auth;
        this.documents = new DocumentService(session);
    }
}
