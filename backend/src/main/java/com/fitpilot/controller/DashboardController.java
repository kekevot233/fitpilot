package com.fitpilot.controller;

import com.fitpilot.dto.food.DailyDashboardResponse;
import com.fitpilot.service.FoodLogService;
import com.fitpilot.service.UserAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Profile("!no-db")
@RequestMapping("/api/users/{userId}/dashboard")
@Tag(name = "Dashboard", description = "指定日期的营养目标、摄入、剩余值和饮食记录")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final FoodLogService foodLogService;
    private final UserAccessGuard userAccessGuard;

    public DashboardController(FoodLogService foodLogService, UserAccessGuard userAccessGuard) {
        this.foodLogService = foodLogService;
        this.userAccessGuard = userAccessGuard;
    }

    @GetMapping
    @Operation(summary = "查询每日 Dashboard")
    public DailyDashboardResponse getDailyDashboard(
            @PathVariable long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        userAccessGuard.check(userId);
        return foodLogService.getDailyDashboard(userId, date == null ? LocalDate.now() : date);
    }
}
