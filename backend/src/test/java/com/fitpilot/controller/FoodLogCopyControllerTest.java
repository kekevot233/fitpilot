package com.fitpilot.controller;

import com.fitpilot.config.SecurityConfig;
import com.fitpilot.service.FoodLogService;
import com.fitpilot.service.UserAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = FoodLogCopyController.class, properties = {
        "fitpilot.security.jwt.secret=copy-day-test-secret-at-least-32-bytes",
        "fitpilot.security.jwt.issuer=fitpilot-test"
})
@Import({SecurityConfig.class, UserAccessGuard.class})
class FoodLogCopyControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private FoodLogService foodLogService;
    @MockitoBean
    private JwtDecoder decoder;

    private void authenticate() {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "HS256").subject("7").claim("userId", 7L).build());
    }

    @Test
    void copiesOnlyForTheAuthenticatedUser() throws Exception {
        authenticate();

        mvc.perform(post("/api/me/food-logs/copy-day")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceDate\":\"2026-10-10\",\"targetDate\":\"2026-10-11\"}"))
                .andExpect(status().isCreated());

        verify(foodLogService).copyDay(
                7L, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11));
    }

    @Test
    void rejectsUnauthenticatedCopy() throws Exception {
        mvc.perform(post("/api/me/food-logs/copy-day")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceDate\":\"2026-10-10\",\"targetDate\":\"2026-10-11\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(foodLogService);
    }

    @Test
    void rejectsMissingDates() throws Exception {
        authenticate();
        mvc.perform(post("/api/me/food-logs/copy-day")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(foodLogService);
    }
}
