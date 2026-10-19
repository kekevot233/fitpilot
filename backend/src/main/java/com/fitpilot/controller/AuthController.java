package com.fitpilot.controller;

import com.fitpilot.dto.auth.AuthResponse;
import com.fitpilot.dto.auth.LoginRequest;
import com.fitpilot.dto.auth.RegisterRequest;
import com.fitpilot.dto.user.UserProfileResponse;
import com.fitpilot.service.AuthService;
import com.fitpilot.service.UserAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.fitpilot.service.LoginClientAddress;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!no-db")
@RequestMapping("/api/auth")
@Tag(name = "身份认证", description = "用户注册、登录与当前用户")
public class AuthController {

    private final AuthService authService;
    private final UserAccessGuard userAccessGuard;
    private final LoginClientAddress clientAddress;

    public AuthController(AuthService authService, UserAccessGuard userAccessGuard, LoginClientAddress clientAddress) {
        this.authService = authService;
        this.userAccessGuard = userAccessGuard;
        this.clientAddress = clientAddress;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "注册用户并生成营养目标")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "邮箱密码登录")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, clientAddress.resolve(httpRequest));
    }

    @GetMapping("/me")
    @Operation(summary = "读取当前登录用户")
    @SecurityRequirement(name = "bearerAuth")
    public UserProfileResponse me() {
        return authService.getCurrentUser(userAccessGuard.currentUserId());
    }
}
