package com.desktop.service;

import com.desktop.api.Dto;
import com.desktop.crypto.InviteCrypto;
import com.desktop.crypto.UserKeys;
import tools.jackson.core.type.TypeReference;

import java.util.List;

/**
 * Kişi listesi (Crypto_Design §7.7–7.8). Sunucudaki kayıt bu bilgisayarda denetlenir: kayıttaki parmak izi kendi
 * imzamızla doğrulanır ve kişinin güncel anahtarlarından hesaplananla karşılaştırılır. Sunucunun söylediği durum tek
 * başına yeterli sayılmaz.
 */
public final class ContactService {

    public enum State {
        /** İmza geçerli, parmak izi güncel anahtarla aynı: kodsuz gönderilebilir. */
        VERIFIED,
        /** Karşı taraf kodu girdi; bu bilgisayar kabul mührünü henüz doğrulamadı. */
        PENDING,
        /** Kişinin anahtarı değişti (parolasını sıfırlamış olabilir): yeni davet kodu gerekir. */
        KEY_CHANGED,
        /** Kayıt bizim imzamızı taşımıyor: sunucudaki kayıt değiştirilmiş olabilir. Gönderim yapılmaz. */
        UNTRUSTED
    }

    public record Contact(Long userId, String fullName, String phone, State state, String fingerprint,
                          String encryptionPublicKey, String signingPublicKey) {

        public boolean canReceive() {
            return state == State.VERIFIED;
        }

        public Dto.Recipient recipient() {
            return new Dto.Recipient(userId, fullName, encryptionPublicKey, signingPublicKey);
        }
    }

    private final Session session;

    public ContactService(Session session) {
        this.session = session;
    }

    public List<Contact> list() {
        List<Dto.Contact> contacts = session.api().get("/api/contacts", new TypeReference<>() {
        });
        return contacts.stream().map(this::evaluate).toList();
    }

    public void delete(Long userId) {
        session.api().delete("/api/contacts/" + userId);
    }

    Contact evaluate(Dto.Contact c) {
        String current = c.encryptionPublicKey() == null || c.signingPublicKey() == null ? null
                : UserKeys.fingerprint(c.encryptionPublicKey(), c.signingPublicKey());
        State state;
        if (c.status() == Dto.ContactStatus.PENDING) {
            state = State.PENDING;
        } else if (!InviteCrypto.contactSignatureValid(session.keys(), session.user().phone(), c.phone(),
                c.fingerprint(), c.signature())) {
            state = State.UNTRUSTED;
        } else if (current == null || !current.equals(c.fingerprint())) {
            state = State.KEY_CHANGED;
        } else {
            state = State.VERIFIED;
        }
        return new Contact(c.userId(), c.fullName(), c.phone(), state, current, c.encryptionPublicKey(),
                c.signingPublicKey());
    }
}
