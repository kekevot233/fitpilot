package com.fitpilot.controller;

import com.fitpilot.dto.food.CreateFoodLogRequest;
import com.fitpilot.dto.food.FoodLogResponse;
import com.fitpilot.service.FoodLogService;
import com.fitpilot.service.UserAccessGuard;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!no-db")
@RequestMapping("/api/users/{userId}/food-logs")
@Tag(name = "饮食记录", description = "新增、修改和删除用户饮食记录")
@SecurityRequirement(name = "bearerAuth")
public class FoodLogController {

    private final FoodLogService foodLogService;
    private final UserAccessGuard userAccessGuard;

    public FoodLogController(FoodLogService foodLogService, UserAccessGuard userAccessGuard) {
        this.foodLogService = foodLogService;
        this.userAccessGuard = userAccessGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增饮食记录")
    public FoodLogResponse create(@PathVariable long userId,
                                  @Valid @RequestBody CreateFoodLogRequest request) {
        userAccessGuard.check(userId);
        return foodLogService.create(userId, request);
    }

    @PutMapping("/{foodLogId}")
    @Operation(summary = "修改饮食记录")
    public FoodLogResponse update(@PathVariable long userId,
                                  @PathVariable long foodLogId,
                                  @Valid @RequestBody CreateFoodLogRequest request) {
        userAccessGuard.check(userId);
        return foodLogService.update(userId, foodLogId, request);
    }

    @DeleteMapping("/{foodLogId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除饮食记录")
    public void delete(@PathVariable long userId, @PathVariable long foodLogId) {
        userAccessGuard.check(userId);
        foodLogService.delete(userId, foodLogId);
    }
}
