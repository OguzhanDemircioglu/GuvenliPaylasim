package com.server.modules.contact;

import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponse;
import com.server.modules.contact.dto.ContactResponse;
import com.server.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Kullanıcı yalnızca kendi kişi listesini görür ve değiştirir. Kişi ekleme davet koduyla olur (InviteController). */
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService service;
    private final CurrentUser currentUser;

    @GetMapping
    public ApiResponse<List<ContactResponse>> list() {
        return ApiResponse.success(service.list(currentUser.id()));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> delete(@PathVariable Long userId) {
        service.delete(currentUser.id(), userId);
        return ApiResponse.success(GlobalMessages.RECORD_DELETED, null);
    }
}
