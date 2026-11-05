package com.desktop.service;

import com.desktop.api.Dto;
import com.desktop.crypto.PasswordKeys;
import tools.jackson.core.type.TypeReference;

import java.util.List;

/**
 * Birim ve kullanıcı yönetimi. Yöneticinin belirlediği ilk parola da sunucuya türetilmiş haliyle gider;
 * kullanıcı ilk girişte parolasını değiştirmek zorundadır.
 */
public final class AdminService {

    private final Session session;

    public AdminService(Session session) {
        this.session = session;
    }

    public List<Dto.Unit> units() {
        return session.api().get("/api/units", new TypeReference<>() {
        });
    }

    public void saveUnit(Dto.Unit existing, String name, String description) {
        if (existing == null) {
            session.api().post("/api/units", new Dto.UnitRequest(name, description, null), new TypeReference<Object>() {
            });
        } else {
            session.api().put("/api/units/" + existing.id(), new Dto.UnitRequest(name, description, existing.version()),
                    new TypeReference<Object>() {
                    });
        }
    }

    public void deleteUnit(Dto.Unit unit) {
        session.api().delete("/api/units/" + unit.id());
    }

    public List<Dto.User> users() {
        return session.api().get("/api/users", new TypeReference<>() {
        });
    }

    public void createUser(String email, String fullName, char[] initialPassword, Dto.Role role, Long unitId,
                           String phone) {
        String secret = PasswordKeys.derive(email, initialPassword).authSecret();
        session.api().post("/api/users",
                new Dto.UserCreateRequest(email.trim(), fullName, secret, role, unitId, blankToNull(phone)),
                new TypeReference<Object>() {
                });
    }

    public void updateUser(Dto.User user, String fullName, Dto.Role role, boolean active, Long unitId, String phone) {
        session.api().put("/api/users/" + user.id(),
                new Dto.UserUpdateRequest(user.email(), fullName, role, active, unitId, blankToNull(phone), user.version()),
                new TypeReference<Object>() {
                });
    }

    /** Kullanıcının anahtarları silinir; eski belgelerini artık açamaz (uçtan uca şifrelemenin doğal sonucu). */
    public void resetPassword(Dto.User user, char[] newPassword) {
        String secret = PasswordKeys.derive(user.email(), newPassword).authSecret();
        session.api().put("/api/users/" + user.id() + "/password", new Dto.PasswordResetRequest(secret),
                new TypeReference<Object>() {
                });
    }

    public void deleteUser(Dto.User user) {
        session.api().delete("/api/users/" + user.id());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
