package com.fitpilot.controller;

import com.fitpilot.config.SecurityConfig;
import com.fitpilot.service.NutritionTrendService;
import com.fitpilot.service.UserAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = NutritionTrendController.class, properties = {
        "fitpilot.security.jwt.secret=nutrition-trend-test-secret-at-least-32-bytes",
        "fitpilot.security.jwt.issuer=fitpilot-test"
})
@Import({SecurityConfig.class, UserAccessGuard.class})
class NutritionTrendControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private NutritionTrendService service;
    @MockitoBean
    private JwtDecoder decoder;

    private void authenticate() {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "HS256").subject("7").claim("userId", 7L).build());
    }

    @Test
    void usesTheAuthenticatedUserAndRequestedDateRange() throws Exception {
        authenticate();

        mvc.perform(get("/api/me/nutrition-trend?endDate=2026-10-11&days=7")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());

        verify(service).getTrend(7L, LocalDate.of(2026, 10, 11), 7);
    }

    @Test
    void rejectsAnUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/api/me/nutrition-trend"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnInvalidNumberOfDays() throws Exception {
        authenticate();

        mvc.perform(get("/api/me/nutrition-trend?days=31")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
