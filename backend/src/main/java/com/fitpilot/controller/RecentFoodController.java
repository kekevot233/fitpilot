package com.fitpilot.controller;

import com.fitpilot.dto.food.FoodResponse;
import com.fitpilot.service.FoodService;
import com.fitpilot.service.UserAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@Profile("!no-db")
@RequestMapping("/api/me/foods")
@Tag(name = "食物库", description = "食物及其每100克营养数据")
@SecurityRequirement(name = "bearerAuth")
public class RecentFoodController {

    private final FoodService foodService;
    private final UserAccessGuard userAccessGuard;

    public RecentFoodController(FoodService foodService, UserAccessGuard userAccessGuard) {
        this.foodService = foodService;
        this.userAccessGuard = userAccessGuard;
    }

    @GetMapping("/recent")
    @Operation(summary = "查询当前用户最近记录的食物")
    public List<FoodResponse> recent(
            @RequestParam(defaultValue = "6") @Min(1) @Max(12) int limit) {
        return foodService.findRecentlyLoggedFoods(userAccessGuard.currentUserId(), limit);
    }
}
