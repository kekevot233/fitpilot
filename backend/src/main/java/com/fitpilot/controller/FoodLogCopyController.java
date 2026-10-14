package com.fitpilot.controller;

import com.fitpilot.dto.food.CopyFoodLogsRequest;
import com.fitpilot.dto.food.CopyFoodLogsResponse;
import com.fitpilot.service.FoodLogService;
import com.fitpilot.service.UserAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!no-db")
@RequestMapping("/api/me/food-logs")
@Tag(name = "饮食记录", description = "新增、修改、删除和复制用户饮食记录")
@SecurityRequirement(name = "bearerAuth")
public class FoodLogCopyController {

    private final FoodLogService foodLogService;
    private final UserAccessGuard userAccessGuard;

    public FoodLogCopyController(FoodLogService foodLogService, UserAccessGuard userAccessGuard) {
        this.foodLogService = foodLogService;
        this.userAccessGuard = userAccessGuard;
    }

    @PostMapping("/copy-day")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "将一天的饮食记录复制到另一天", description = "目标日期必须尚无饮食记录")
    public CopyFoodLogsResponse copyDay(@Valid @RequestBody CopyFoodLogsRequest request) {
        return foodLogService.copyDay(
                userAccessGuard.currentUserId(), request.sourceDate(), request.targetDate()
        );
    }
}
