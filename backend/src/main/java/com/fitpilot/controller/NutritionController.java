package com.fitpilot.controller;

import com.fitpilot.dto.nutrition.NutritionCalculationRequest;
import com.fitpilot.dto.nutrition.NutritionCalculationResponse;
import com.fitpilot.service.NutritionCalculationService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nutrition")
@Tag(name = "营养计算", description = "BMR、TDEE 与每日三大营养素目标")
public class NutritionController {

    private final NutritionCalculationService nutritionCalculationService;

    public NutritionController(NutritionCalculationService nutritionCalculationService) {
        this.nutritionCalculationService = nutritionCalculationService;
    }

    @PostMapping("/calculate")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "计算营养目标", description = "只计算并返回结果，不保存到数据库")
    public NutritionCalculationResponse calculate(@Valid @RequestBody NutritionCalculationRequest request) {
        return nutritionCalculationService.calculate(request);
    }
}
