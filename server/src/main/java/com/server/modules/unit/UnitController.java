package com.server.modules.unit;

import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponse;
import com.server.modules.unit.dto.UnitCreateRequest;
import com.server.modules.unit.dto.UnitResponse;
import com.server.modules.unit.dto.UnitUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Birimler: herkes listeler (alıcı seçimi için), sadece ADMIN ekler, değiştirir ve siler.
 */
@RestController
@RequestMapping("/api/units")
@RequiredArgsConstructor
public class UnitController {

    private final UnitService service;

    @GetMapping
    public ApiResponse<List<UnitResponse>> getAll() {
        return ApiResponse.success(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<UnitResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(service.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResponse<UnitResponse> create(@Valid @RequestBody UnitCreateRequest request) {
        return ApiResponse.success(GlobalMessages.RECORD_CREATED, service.create(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<UnitResponse> update(@PathVariable Long id, @Valid @RequestBody UnitUpdateRequest request) {
        return ApiResponse.success(GlobalMessages.RECORD_UPDATED, service.update(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(GlobalMessages.RECORD_DELETED, null);
    }
}
