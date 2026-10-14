package com.fitpilot.controller;

import com.fitpilot.dto.user.UpdateNutritionPlanRequest;
import com.fitpilot.dto.user.UserProfileResponse;
import com.fitpilot.service.UserAccessGuard;
import com.fitpilot.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!no-db")
@RequestMapping("/api/me")
@Tag(name = "饮食计划", description = "调整当前用户的饮食目标")
@SecurityRequirement(name = "bearerAuth")
public class NutritionPlanController {

    private final UserProfileService userProfileService;
    private final UserAccessGuard userAccessGuard;

    public NutritionPlanController(UserProfileService userProfileService, UserAccessGuard userAccessGuard) {
        this.userProfileService = userProfileService;
        this.userAccessGuard = userAccessGuard;
    }

    @PutMapping("/nutrition-plan")
    @Operation(summary = "调整饮食计划，重新计算营养目标并从今天生效")
    public UserProfileResponse update(@Valid @RequestBody UpdateNutritionPlanRequest request) {
        return userProfileService.updateNutritionPlan(userAccessGuard.currentUserId(), request.goal());
    }
}
