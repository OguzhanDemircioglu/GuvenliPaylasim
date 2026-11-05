package com.server.modules.document;

import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponse;
import com.server.modules.document.dto.DocumentContentResponse;
import com.server.modules.document.dto.DocumentSendRequest;
import com.server.modules.document.dto.DocumentSentResponse;
import com.server.modules.document.dto.InboxItemResponse;
import com.server.modules.document.dto.SentItemResponse;
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

/** Her kullanıcı yalnızca kendi gönderdiği ve kendisine gelen belgeleri görür. */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService service;
    private final CurrentUser currentUser;

    @PostMapping
    public ApiResponse<DocumentSentResponse> send(@Valid @RequestBody DocumentSendRequest request) {
        return ApiResponse.success(GlobalMessages.RECORD_CREATED, service.send(currentUser.id(), request));
    }

    @GetMapping("/inbox")
    public ApiResponse<List<InboxItemResponse>> inbox() {
        return ApiResponse.success(service.inbox(currentUser.id()));
    }

    @GetMapping("/sent")
    public ApiResponse<List<SentItemResponse>> sent() {
        return ApiResponse.success(service.sent(currentUser.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentContentResponse> open(@PathVariable Long id) {
        return ApiResponse.success(service.open(id, currentUser.id()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id, currentUser.id());
        return ApiResponse.success(GlobalMessages.RECORD_DELETED, null);
    }
}
