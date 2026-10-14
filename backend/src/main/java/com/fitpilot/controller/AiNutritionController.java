package com.fitpilot.controller;

import com.fitpilot.dto.food.AiNutritionEstimateResponse;
import com.fitpilot.dto.food.EstimateFoodNutritionRequest;
import com.fitpilot.service.AiNutritionEstimateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!no-db")
@RequestMapping("/api/me/ai-nutrition")
@Tag(name = "AI 营养估算", description = "使用大模型按菜品名称和克数估算营养")
@SecurityRequirement(name = "bearerAuth")
public class AiNutritionController {

    private final AiNutritionEstimateService estimateService;

    public AiNutritionController(AiNutritionEstimateService estimateService) {
        this.estimateService = estimateService;
    }

    @PostMapping("/estimate")
    @Operation(summary = "估算菜品营养", description = "结果仅为常见做法估算，确认后再写入食物库和饮食记录")
    public AiNutritionEstimateResponse estimate(@Valid @RequestBody EstimateFoodNutritionRequest request) {
        return estimateService.estimate(request);
    }
}
