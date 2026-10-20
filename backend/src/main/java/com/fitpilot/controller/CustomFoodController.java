package com.fitpilot.controller;

import com.fitpilot.dto.food.CreateFoodRequest;
import com.fitpilot.dto.food.FoodResponse;
import com.fitpilot.service.FoodService;
import com.fitpilot.service.UserAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@Profile("!no-db")
@RequestMapping("/api/me/foods/custom")
@Tag(name = "自定义食物", description = "管理当前用户自己创建的食物")
@SecurityRequirement(name = "bearerAuth")
public class CustomFoodController {

    private final FoodService foodService;
    private final UserAccessGuard userAccessGuard;

    public CustomFoodController(FoodService foodService, UserAccessGuard userAccessGuard) {
        this.foodService = foodService;
        this.userAccessGuard = userAccessGuard;
    }

    @GetMapping
    @Operation(summary = "查询当前用户的自定义食物")
    public List<FoodResponse> findAll() {
        return foodService.findCustomFoods(userAccessGuard.currentUserId());
    }

    @PutMapping("/{foodId}")
    @Operation(summary = "修改尚未用于饮食记录的自定义食物")
    public FoodResponse update(@PathVariable @Positive(message = "食物 ID 必须为正整数。") long foodId,
                               @Valid @RequestBody CreateFoodRequest request) {
        return foodService.updateCustomFood(userAccessGuard.currentUserId(), foodId, request);
    }

    @DeleteMapping("/{foodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "归档自定义食物，同时保留已有饮食记录")
    public void archive(@PathVariable @Positive(message = "食物 ID 必须为正整数。") long foodId) {
        foodService.archiveCustomFood(userAccessGuard.currentUserId(), foodId);
    }
}
