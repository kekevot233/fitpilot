package com.fitpilot.controller;

import com.fitpilot.dto.food.CalculateFoodNutritionRequest;
import com.fitpilot.dto.food.CreateFoodRequest;
import com.fitpilot.dto.food.FoodNutritionCalculationResponse;
import com.fitpilot.dto.food.FoodResponse;
import com.fitpilot.service.FoodService;
import com.fitpilot.service.UserAccessGuard;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("!no-db")
@RequestMapping("/api/foods")
@Tag(name = "食物库", description = "食物及其每100克营养数据")
@SecurityRequirement(name = "bearerAuth")
public class FoodController {

    private final FoodService foodService;
    private final UserAccessGuard userAccessGuard;

    public FoodController(FoodService foodService, UserAccessGuard userAccessGuard) {
        this.foodService = foodService;
        this.userAccessGuard = userAccessGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增食物")
    public FoodResponse create(@Valid @RequestBody CreateFoodRequest request) {
        return foodService.create(userAccessGuard.currentUserId(), request);
    }

    @GetMapping
    @Operation(summary = "查询食物", description = "keyword 为空时返回全部食物，否则按名称模糊搜索")
    public List<FoodResponse> findAll(
            @RequestParam(required = false) String keyword) {
        return foodService.findAll(userAccessGuard.currentUserId(), keyword);
    }

    @PostMapping("/calculate")
    @Operation(summary = "按克数试算营养", description = "只计算并返回结果，不保存饮食记录")
    public FoodNutritionCalculationResponse calculate(
            @Valid @RequestBody CalculateFoodNutritionRequest request) {
        return foodService.calculate(userAccessGuard.currentUserId(), request);
    }
}
