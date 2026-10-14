package com.fitpilot.controller;

import com.fitpilot.dto.user.UpdateUserProfileRequest;
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
@Tag(name = "用户资料", description = "修改当前用户的身体资料")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final UserProfileService userProfileService;
    private final UserAccessGuard userAccessGuard;

    public ProfileController(UserProfileService userProfileService, UserAccessGuard userAccessGuard) {
        this.userProfileService = userProfileService;
        this.userAccessGuard = userAccessGuard;
    }

    @PutMapping("/profile")
    @Operation(summary = "修改身体资料，并根据当前饮食目标重新计算营养目标")
    public UserProfileResponse update(@Valid @RequestBody UpdateUserProfileRequest request) {
        return userProfileService.updateProfile(userAccessGuard.currentUserId(), request);
    }
}
