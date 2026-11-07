package com.server.modules.user;

import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponse;
import com.server.modules.user.dto.DeleteAccountRequest;
import com.server.modules.user.dto.UpdateNameRequest;
import com.server.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Giriş yapmış kullanıcı sadece kendi profilini değiştirir; kullanıcı kimliği oturumdan gelir. */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;
    private final CurrentUser currentUser;

    @PutMapping("/name")
    public ApiResponse<Void> updateName(@Valid @RequestBody UpdateNameRequest request) {
        userService.updateName(currentUser.id(), request.fullName());
        return ApiResponse.success(GlobalMessages.RECORD_UPDATED, null);
    }

    /** Hesabı ve ilgili her şeyi kalıcı olarak siler (AccountDeletionService). Parola, gerekirse TOTP kodu istenir. */
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody DeleteAccountRequest request) {
        accountDeletionService.delete(currentUser.id(), request.password(), request.totpCode());
        return ApiResponse.success(GlobalMessages.RECORD_DELETED, null);
    }
}
