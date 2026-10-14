package com.fitpilot.service;

import com.fitpilot.domain.User;
import com.fitpilot.dto.auth.AuthResponse;
import com.fitpilot.dto.auth.LoginRequest;
import com.fitpilot.dto.auth.RegisterRequest;
import com.fitpilot.dto.user.UserProfileResponse;
import com.fitpilot.exception.InvalidCredentialsException;
import com.fitpilot.exception.ResourceConflictException;
import com.fitpilot.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Profile("!no-db")
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileService userProfileService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository,
                       UserProfileService userProfileService,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.userProfileService = userProfileService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResourceConflictException("This email is already registered.");
        }

        RegisterRequest normalized = new RegisterRequest(
                email, request.password(), request.nickname(), request.gender(), request.age(),
                request.heightCm(), request.weightKg(), request.activityLevel(), request.goal(),
                request.weeklyTrainingFrequency()
        );
        UserProfileResponse profile = userProfileService.createRegisteredProfile(
                normalized, passwordEncoder.encode(request.password())
        );
        User user = userRepository.findById(profile.id()).orElseThrow();
        return response(user, profile);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(this::invalidCredentials);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        return response(user, userProfileService.getProfile(user.getId()));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(long userId) {
        return userProfileService.getProfile(userId);
    }

    private AuthResponse response(User user, UserProfileResponse profile) {
        TokenService.IssuedToken token = tokenService.issue(user);
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), profile);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private InvalidCredentialsException invalidCredentials() {
        return new InvalidCredentialsException("Email or password is incorrect.");
    }
}
