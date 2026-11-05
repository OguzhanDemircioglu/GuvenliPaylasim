package com.server.modules.user;

import com.server.common.service.BaseService;
import com.server.modules.user.dto.UserCreateRequest;
import com.server.modules.user.dto.UserPasswordResetRequest;
import com.server.modules.user.dto.UserResponse;
import com.server.modules.user.dto.UserUpdateRequest;

public interface UserService extends BaseService<UserCreateRequest, UserUpdateRequest, UserResponse> {

    /** Admin'in bir kullanıcının şifresini sıfırlaması; kullanıcının bütün oturumları kapanır. */
    void resetPassword(Long id, UserPasswordResetRequest request);

    /**
     * Kayıt formundan gelen kullanıcı: rolü her zaman USER'dır. Giriş yapmış biri olmadığı için {@link #create}'teki
     * yönetim kapsamı kontrolü yapılmaz.
     */
    UserResponse register(String email, String fullName, String password);
}
