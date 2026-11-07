package com.desktop.ui.views;

import com.desktop.service.AuthService;
import com.desktop.service.ContactService;
import com.desktop.service.DocumentService;
import com.desktop.service.InviteService;
import com.desktop.service.Session;

/** Ekranların ortak ihtiyaçları: oturum ve servisler. */
final class Context {

    final Session session;
    final AuthService auth;
    final DocumentService documents;
    final ContactService contacts;
    final InviteService invites;
    /** Gelen kutusu değişince kenar çubuğundaki sayaç yenilenir. */
    Runnable onInboxChanged = () -> {
    };
    /** Profilde ad soyad değişince kenar çubuğundaki ad yenilenir. */
    Runnable onProfileChanged = () -> {
    };
    /** Hesap silinince giriş ekranına dönülür. */
    Runnable onAccountDeleted = () -> {
    };

    Context(Session session, AuthService auth) {
        this.session = session;
        this.auth = auth;
        this.documents = new DocumentService(session);
        this.contacts = new ContactService(session);
        this.invites = new InviteService(session);
    }
}
