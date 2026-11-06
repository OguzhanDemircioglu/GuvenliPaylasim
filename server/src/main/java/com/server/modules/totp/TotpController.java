package com.server.modules.totp;

import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponse;
import com.server.modules.totp.dto.TotpCodeRequest;
import com.server.modules.totp.dto.TotpDisableRequest;
import com.server.modules.totp.dto.TotpEnabledResponse;
import com.server.modules.totp.dto.TotpSetupResponse;
import com.server.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Giriş yapmış kullanıcı kendi iki adımlı doğrulamasını açar ya da kapatır. */
@RestController
@RequestMapping("/api/auth/totp")
@RequiredArgsConstructor
public class TotpController {

    private final TotpService service;
    private final CurrentUser currentUser;

    @PostMapping("/setup")
    public ApiResponse<TotpSetupResponse> setup() {
        return ApiResponse.success(service.setup(currentUser.id()));
    }

    @PostMapping("/enable")
    public ApiResponse<TotpEnabledResponse> enable(@Valid @RequestBody TotpCodeRequest request) {
        return ApiResponse.success(GlobalMessages.RECORD_UPDATED, service.enable(currentUser.id(), request.code()));
    }

    @PostMapping("/disable")
    public ApiResponse<Void> disable(@Valid @RequestBody TotpDisableRequest request) {
        service.disable(currentUser.id(), request.password(), request.code());
        return ApiResponse.success(GlobalMessages.RECORD_UPDATED, null);
    }
}
