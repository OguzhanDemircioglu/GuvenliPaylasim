package com.server.modules.report;

import com.server.common.response.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Grafiklerin beslendiği özet endpoint'ler. Referans örnek: anasayfadaki "son 30 günde eklenen kategoriler" grafiği.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;

    @GetMapping("/categories-created")
    public ApiResponse<List<PeriodValue>> categoriesCreated(
            @RequestParam(defaultValue = "30")
            @Min(value = 1, message = ReportMessages.DAYS_INVALID)
            @Max(value = 365, message = ReportMessages.DAYS_INVALID)
            int days) {
        return ApiResponse.success(service.categoriesCreatedPerDay(days));
    }
}
