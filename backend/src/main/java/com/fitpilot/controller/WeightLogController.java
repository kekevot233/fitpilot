package com.fitpilot.controller;

import com.fitpilot.dto.weight.UpsertWeightLogRequest;
import com.fitpilot.dto.weight.WeightLogResponse;
import com.fitpilot.dto.weight.WeightTrendResponse;
import com.fitpilot.service.UserAccessGuard;
import com.fitpilot.service.WeightLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Validated
@Profile("!no-db")
@RequestMapping("/api/me")
@Tag(name = "体重趋势", description = "记录每日体重并查看趋势")
@SecurityRequirement(name = "bearerAuth")
public class WeightLogController {

    private final WeightLogService weightLogService;
    private final UserAccessGuard userAccessGuard;

    public WeightLogController(WeightLogService weightLogService, UserAccessGuard userAccessGuard) {
        this.weightLogService = weightLogService;
        this.userAccessGuard = userAccessGuard;
    }

    @PutMapping("/weight-logs/{recordedDate}")
    @Operation(summary = "新增或修改某一天的体重")
    public WeightLogResponse upsert(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordedDate,
            @Valid @RequestBody UpsertWeightLogRequest request) {
        return weightLogService.upsert(userAccessGuard.currentUserId(), recordedDate, request);
    }

    @GetMapping("/weight-trend")
    @Operation(summary = "查询体重和 7 日移动平均趋势")
    public WeightTrendResponse getTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "30") @Min(7) @Max(90) int days) {
        return weightLogService.getTrend(
                userAccessGuard.currentUserId(),
                endDate == null ? LocalDate.now() : endDate,
                days
        );
    }
}
