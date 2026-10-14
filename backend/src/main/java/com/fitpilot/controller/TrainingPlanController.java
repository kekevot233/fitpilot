package com.fitpilot.controller;

import com.fitpilot.dto.training.TrainingPlanResponse;
import com.fitpilot.service.TrainingPlanService;
import com.fitpilot.service.UserAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!no-db")
@RequestMapping("/api/users/{userId}/training-plans")
@Tag(name = "训练计划", description = "生成和读取基础训练计划")
@SecurityRequirement(name = "bearerAuth")
public class TrainingPlanController {

    private final TrainingPlanService trainingPlanService;
    private final UserAccessGuard userAccessGuard;

    public TrainingPlanController(TrainingPlanService trainingPlanService,
                                  UserAccessGuard userAccessGuard) {
        this.trainingPlanService = trainingPlanService;
        this.userAccessGuard = userAccessGuard;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "根据每周训练频率生成计划")
    public TrainingPlanResponse generate(@PathVariable long userId) {
        userAccessGuard.check(userId);
        return trainingPlanService.generate(userId);
    }

    @GetMapping("/current")
    @Operation(summary = "查询当前生效的训练计划")
    public TrainingPlanResponse getCurrent(@PathVariable long userId) {
        userAccessGuard.check(userId);
        return trainingPlanService.getCurrent(userId);
    }
}
