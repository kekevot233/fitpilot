package com.fitpilot.controller;

import com.fitpilot.dto.training.CreateWorkoutRequest;
import com.fitpilot.dto.training.WorkoutResponse;
import com.fitpilot.service.WorkoutService;
import com.fitpilot.service.UserAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("!no-db")
@RequestMapping("/api/users/{userId}/workouts")
@Tag(name = "训练记录", description = "保存和查询实际完成的动作组")
@SecurityRequirement(name = "bearerAuth")
public class WorkoutController {

    private final WorkoutService workoutService;
    private final UserAccessGuard userAccessGuard;

    public WorkoutController(WorkoutService workoutService, UserAccessGuard userAccessGuard) {
        this.workoutService = workoutService;
        this.userAccessGuard = userAccessGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "保存一次训练及全部训练组")
    public WorkoutResponse create(@PathVariable long userId,
                                  @Valid @RequestBody CreateWorkoutRequest request) {
        userAccessGuard.check(userId);
        return workoutService.create(userId, request);
    }

    @GetMapping
    @Operation(summary = "查询用户训练历史")
    public List<WorkoutResponse> findAll(@PathVariable long userId) {
        userAccessGuard.check(userId);
        return workoutService.findAll(userId);
    }
}
