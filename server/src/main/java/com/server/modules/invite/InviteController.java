package com.server.modules.invite;

import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponse;
import com.server.modules.invite.dto.InviteAcceptRequest;
import com.server.modules.invite.dto.InviteAcceptedResponse;
import com.server.modules.invite.dto.InviteCreateRequest;
import com.server.modules.invite.dto.InviteCreatedResponse;
import com.server.modules.invite.dto.InviteRedeemRequest;
import com.server.modules.invite.dto.InviteRedeemResponse;
import com.server.modules.invite.dto.InviteVerificationResponse;
import com.server.modules.invite.dto.InviteVerifyRequest;
import com.server.modules.invite.dto.SentInviteResponse;
import com.server.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Davet kodu (Crypto_Design §7.6). Her işlem giriş yapmış kullanıcıya göre süzülür: alıcı sadece kendi telefonuna
 * bırakılmış daveti, gönderen sadece kendi davetlerini görür.
 */
@RestController
@RequestMapping("/api/invites")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService service;
    private final CurrentUser currentUser;

    @PostMapping
    public ApiResponse<InviteCreatedResponse> create(@Valid @RequestBody InviteCreateRequest request) {
        return ApiResponse.success(GlobalMessages.RECORD_CREATED, service.create(currentUser.id(), request));
    }

    @PostMapping("/redeem")
    public ApiResponse<InviteRedeemResponse> redeem(@Valid @RequestBody InviteRedeemRequest request) {
        return ApiResponse.success(service.redeem(currentUser.id(), request.verifier()));
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<InviteAcceptedResponse> accept(@PathVariable Long id,
                                                      @Valid @RequestBody InviteAcceptRequest request) {
        return ApiResponse.success(service.accept(currentUser.id(), id, request));
    }

    @GetMapping("/verifications")
    public ApiResponse<List<InviteVerificationResponse>> verifications() {
        return ApiResponse.success(service.pendingVerifications(currentUser.id()));
    }

    @PostMapping("/{id}/verify")
    public ApiResponse<Void> verify(@PathVariable Long id, @Valid @RequestBody InviteVerifyRequest request) {
        service.verify(currentUser.id(), id, request);
        return ApiResponse.success(GlobalMessages.RECORD_UPDATED, null);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id) {
        service.reject(currentUser.id(), id);
        return ApiResponse.success(GlobalMessages.RECORD_UPDATED, null);
    }

    @GetMapping("/sent")
    public ApiResponse<List<SentInviteResponse>> sent() {
        return ApiResponse.success(service.sent(currentUser.id()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        service.cancel(currentUser.id(), id);
        return ApiResponse.success(GlobalMessages.RECORD_DELETED, null);
    }
}
