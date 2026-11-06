package com.server.modules.auth.dto;

import com.server.modules.user.User;

/** {@code phone} E.164 biçimindedir; istemci parola türetmede tuz olarak bunu kullanır. */
public record SessionUser(Long id, String phone, String fullName) {

    public static SessionUser from(User user) {
        return new SessionUser(user.getId(), user.getPhone(), user.getFullName());
    }
}
