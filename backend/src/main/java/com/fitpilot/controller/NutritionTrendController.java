package com.fitpilot.controller;

import com.fitpilot.dto.food.NutritionTrendResponse;
import com.fitpilot.service.NutritionTrendService;
import com.fitpilot.service.UserAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Validated
@Profile("!no-db")
@RequestMapping("/api/me")
@Tag(name = "饮食趋势", description = "按日期范围查看热量与营养摄入趋势")
@SecurityRequirement(name = "bearerAuth")
public class NutritionTrendController {

    private final NutritionTrendService nutritionTrendService;
    private final UserAccessGuard userAccessGuard;

    public NutritionTrendController(NutritionTrendService nutritionTrendService,
                                    UserAccessGuard userAccessGuard) {
        this.nutritionTrendService = nutritionTrendService;
        this.userAccessGuard = userAccessGuard;
    }

    @GetMapping("/nutrition-trend")
    @Operation(summary = "查询最近一段时间的饮食趋势")
    public NutritionTrendResponse getTrend(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "7") @Min(7) @Max(30) int days) {
        return nutritionTrendService.getTrend(
                userAccessGuard.currentUserId(),
                endDate == null ? LocalDate.now() : endDate,
                days
        );
    }
}
