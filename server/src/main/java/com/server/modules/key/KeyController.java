package com.server.modules.key;

import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponse;
import com.server.modules.key.dto.KeyBundleRequest;
import com.server.modules.key.dto.KeyBundleResponse;
import com.server.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anahtar dizini. Kullanıcı yalnızca kendi şifreli özel anahtar paketini okuyabilir; açık anahtarlar herkese açıktır.
 */
@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
public class KeyController {

    private final KeyService service;
    private final CurrentUser currentUser;

    @GetMapping("/me")
    public ApiResponse<KeyBundleResponse> getOwn() {
        return ApiResponse.success(service.getOwn(currentUser.id()));
    }

    @PostMapping("/me")
    public ApiResponse<KeyBundleResponse> create(@Valid @RequestBody KeyBundleRequest request) {
        return ApiResponse.success(GlobalMessages.RECORD_CREATED, service.create(currentUser.id(), request));
    }
}
