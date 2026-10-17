package com.fitpilot.service;

import com.fitpilot.domain.ActivityLevel;
import com.fitpilot.domain.Gender;
import com.fitpilot.domain.Goal;
import com.fitpilot.domain.User;
import com.fitpilot.domain.WeightLog;
import com.fitpilot.dto.weight.UpsertWeightLogRequest;
import com.fitpilot.dto.weight.WeightLogResponse;
import com.fitpilot.dto.weight.WeightTrendResponse;
import com.fitpilot.repository.UserRepository;
import com.fitpilot.repository.WeightLogRepository;
import com.fitpilot.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WeightLogServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WeightLogRepository weightLogRepository;

    @InjectMocks
    private WeightLogService service;

    @Test
    void createsAWeightLogWhenTheUserHasNoRecordForThatDate() {
        long userId = 1L;
        LocalDate date = LocalDate.of(2026, 10, 10);
        User user = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(weightLogRepository.findByUserIdAndRecordedDate(userId, date)).thenReturn(Optional.empty());
        when(weightLogRepository.save(any(WeightLog.class))).thenAnswer(invocation -> {
            WeightLog saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 8L);
            return saved;
        });

        WeightLogResponse response = service.upsert(userId, date, new UpsertWeightLogRequest(new BigDecimal("75.80")));

        ArgumentCaptor<WeightLog> savedLog = ArgumentCaptor.forClass(WeightLog.class);
        verify(weightLogRepository).save(savedLog.capture());
        assertThat(savedLog.getValue().getRecordedDate()).isEqualTo(date);
        assertThat(savedLog.getValue().getWeightKg()).isEqualByComparingTo("75.80");
        assertThat(response.id()).isEqualTo(8L);
    }

    @Test
    void updatesTheExistingRecordForTheSameDate() {
        long userId = 1L;
        LocalDate date = LocalDate.of(2026, 10, 10);
        WeightLog existing = log("75.80", date);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        when(weightLogRepository.findByUserIdAndRecordedDate(userId, date)).thenReturn(Optional.of(existing));
        when(weightLogRepository.save(existing)).thenReturn(existing);

        service.upsert(userId, date, new UpsertWeightLogRequest(new BigDecimal("75.30")));

        assertThat(existing.getWeightKg()).isEqualByComparingTo("75.30");
        verify(weightLogRepository).save(existing);
    }

    @Test
    void deletesOnlyTheRecordFoundForTheCurrentUserAndDate() {
        LocalDate date = LocalDate.of(2026, 10, 14);
        WeightLog existing = log("75.80", date);
        when(weightLogRepository.findByUserIdAndRecordedDate(7L, date)).thenReturn(Optional.of(existing));

        service.delete(7L, date);

        verify(weightLogRepository).delete(existing);
    }

    @Test
    void refusesDeletionWhenTheCurrentUserHasNoRecordOnThatDate() {
        LocalDate date = LocalDate.of(2026, 10, 14);

        assertThatThrownBy(() -> service.delete(7L, date)).isInstanceOf(ResourceNotFoundException.class);
        verify(weightLogRepository).findByUserIdAndRecordedDate(7L, date);
        verify(weightLogRepository, never()).delete(any());
    }

    @Test
    void calculatesSevenDayAverageUsingOnlyLogsInsideTheCalendarWindow() {
        long userId = 1L;
        LocalDate endDate = LocalDate.of(2026, 10, 10);
        List<WeightLog> logs = List.of(
                log("80.00", LocalDate.of(2026, 10, 3)),
                log("79.00", LocalDate.of(2026, 10, 4)),
                log("78.00", LocalDate.of(2026, 10, 5)),
                log("77.00", LocalDate.of(2026, 10, 8)),
                log("76.00", LocalDate.of(2026, 10, 10))
        );
        when(userRepository.existsById(userId)).thenReturn(true);
        when(weightLogRepository.findByUserIdAndRecordedDateBetweenOrderByRecordedDateAsc(
                eq(userId), any(LocalDate.class), eq(endDate)
        )).thenReturn(logs);

        WeightTrendResponse response = service.getTrend(userId, endDate, 3);

        assertThat(response.points()).hasSize(2);
        assertThat(response.points().getFirst().sevenDayAverageKg()).isEqualByComparingTo("78.50");
        assertThat(response.points().getLast().sevenDayAverageKg()).isEqualByComparingTo("77.50");
        assertThat(response.changeKg()).isEqualByComparingTo("-1.00");
    }

    private User user() {
        return new User(
                "Zhengyi", Gender.MALE, 22, 183.0, 76.0,
                ActivityLevel.MODERATE, Goal.LEAN_BULK, 4
        );
    }

    private WeightLog log(String weight, LocalDate date) {
        WeightLog log = new WeightLog(user(), new BigDecimal(weight), date);
        ReflectionTestUtils.setField(log, "id", (long) date.getDayOfMonth());
        return log;
    }
}
