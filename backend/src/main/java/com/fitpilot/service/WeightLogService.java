package com.fitpilot.service;

import com.fitpilot.domain.User;
import com.fitpilot.domain.WeightLog;
import com.fitpilot.dto.weight.UpsertWeightLogRequest;
import com.fitpilot.dto.weight.WeightLogResponse;
import com.fitpilot.dto.weight.WeightTrendPointResponse;
import com.fitpilot.dto.weight.WeightTrendResponse;
import com.fitpilot.exception.ResourceNotFoundException;
import com.fitpilot.repository.UserRepository;
import com.fitpilot.repository.WeightLogRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Service
@Profile("!no-db")
public class WeightLogService {

    private static final int AVERAGE_WINDOW_DAYS = 7;
    private static final int DISPLAY_SCALE = 2;

    private final UserRepository userRepository;
    private final WeightLogRepository weightLogRepository;

    public WeightLogService(UserRepository userRepository, WeightLogRepository weightLogRepository) {
        this.userRepository = userRepository;
        this.weightLogRepository = weightLogRepository;
    }

    @Transactional
    public WeightLogResponse upsert(long userId, LocalDate recordedDate, UpsertWeightLogRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " was not found."));

        WeightLog log = weightLogRepository.findByUserIdAndRecordedDate(userId, recordedDate)
                .map(existing -> {
                    existing.updateWeight(request.weightKg());
                    return existing;
                })
                .orElseGet(() -> new WeightLog(user, request.weightKg(), recordedDate));

        return toResponse(weightLogRepository.save(log));
    }

    @Transactional(readOnly = true)
    public WeightTrendResponse getTrend(long userId, LocalDate endDate, int days) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User with id " + userId + " was not found.");
        }

        LocalDate startDate = endDate.minusDays(days - 1L);
        LocalDate queryStartDate = startDate.minusDays(AVERAGE_WINDOW_DAYS - 1L);
        List<WeightLog> logs = weightLogRepository
                .findByUserIdAndRecordedDateBetweenOrderByRecordedDateAsc(userId, queryStartDate, endDate);

        Deque<WeightLog> averageWindow = new ArrayDeque<>();
        BigDecimal averageSum = BigDecimal.ZERO;
        List<WeightTrendPointResponse> points = new ArrayList<>();

        for (WeightLog log : logs) {
            averageWindow.addLast(log);
            averageSum = averageSum.add(log.getWeightKg());

            LocalDate earliestIncludedDate = log.getRecordedDate().minusDays(AVERAGE_WINDOW_DAYS - 1L);
            while (!averageWindow.isEmpty()
                    && averageWindow.peekFirst().getRecordedDate().isBefore(earliestIncludedDate)) {
                averageSum = averageSum.subtract(averageWindow.removeFirst().getWeightKg());
            }

            if (!log.getRecordedDate().isBefore(startDate)) {
                points.add(new WeightTrendPointResponse(
                        log.getRecordedDate(),
                        log.getWeightKg(),
                        averageSum.divide(BigDecimal.valueOf(averageWindow.size()), DISPLAY_SCALE, RoundingMode.HALF_UP),
                        averageWindow.size()
                ));
            }
        }

        BigDecimal firstWeight = points.isEmpty() ? null : points.getFirst().weightKg();
        BigDecimal latestWeight = points.isEmpty() ? null : points.getLast().weightKg();
        BigDecimal change = points.size() < 2
                ? null
                : latestWeight.subtract(firstWeight).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);

        return new WeightTrendResponse(startDate, endDate, firstWeight, latestWeight, change, points);
    }

    @Transactional
    public void delete(long userId, LocalDate recordedDate) {
        WeightLog log = weightLogRepository.findByUserIdAndRecordedDate(userId, recordedDate)
                .orElseThrow(() -> new ResourceNotFoundException("No weight log exists on " + recordedDate + "."));
        weightLogRepository.delete(log);
    }

    private WeightLogResponse toResponse(WeightLog log) {
        return new WeightLogResponse(log.getId(), log.getRecordedDate(), log.getWeightKg());
    }
}
