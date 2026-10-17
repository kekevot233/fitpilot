package com.fitpilot.controller;

import com.fitpilot.config.SecurityConfig;
import com.fitpilot.exception.ResourceNotFoundException;
import com.fitpilot.service.UserAccessGuard;
import com.fitpilot.service.WeightLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = WeightLogController.class, properties = {
        "fitpilot.security.jwt.secret=weight-controller-test-secret-at-least-32-bytes",
        "fitpilot.security.jwt.issuer=fitpilot-test"
})
@Import({SecurityConfig.class, UserAccessGuard.class})
class WeightLogControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private WeightLogService weights;
    @MockitoBean
    private JwtDecoder decoder;

    private void authenticate() {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "HS256").subject("7").claim("userId", 7L).build());
    }

    @Test
    void deletesTheAuthenticatedUsersRecordAndReturnsNoContent() throws Exception {
        authenticate();
        mvc.perform(delete("/api/me/weight-logs/2026-10-14?userId=99")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(weights).delete(7L, LocalDate.of(2026, 10, 14));
    }

    @Test
    void rejectsUnauthenticatedDeletion() throws Exception {
        mvc.perform(delete("/api/me/weight-logs/2026-10-14"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(weights);
    }

    @Test
    void rejectsMalformedDateWithoutDeletingAnything() throws Exception {
        authenticate();
        mvc.perform(delete("/api/me/weight-logs/not-a-date")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(weights);
    }

    @Test
    void reportsMissingRecordWithoutExposingOtherUsersRecords() throws Exception {
        authenticate();
        doThrow(new ResourceNotFoundException("No weight log exists on 2026-10-14."))
                .when(weights).delete(7L, LocalDate.of(2026, 10, 14));
        mvc.perform(delete("/api/me/weight-logs/2026-10-14")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound());
    }
}
