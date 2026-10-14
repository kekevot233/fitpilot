package com.fitpilot.controller;

import com.fitpilot.dto.user.UserProfileResponse;
import com.fitpilot.service.UserProfileService;
import com.fitpilot.service.UserAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!no-db")
@RequestMapping("/api/users")
@Tag(name = "用户资料", description = "保存和读取身体资料及当前营养目标")
public class UserController {

    private final UserProfileService userProfileService;
    private final UserAccessGuard userAccessGuard;

    public UserController(UserProfileService userProfileService, UserAccessGuard userAccessGuard) {
        this.userProfileService = userProfileService;
        this.userAccessGuard = userAccessGuard;
    }

    @GetMapping("/{userId}")
    @Operation(summary = "读取用户资料和最新营养目标")
    @SecurityRequirement(name = "bearerAuth")
    public UserProfileResponse getProfile(@PathVariable long userId) {
        userAccessGuard.check(userId);
        return userProfileService.getProfile(userId);
    }
}
