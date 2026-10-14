package com.fitpilot.controller;

import com.fitpilot.config.SecurityConfig;
import com.fitpilot.domain.Goal;
import com.fitpilot.service.UserAccessGuard;
import com.fitpilot.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = NutritionPlanController.class, properties = {
        "fitpilot.security.jwt.secret=nutrition-plan-test-secret-at-least-32-bytes",
        "fitpilot.security.jwt.issuer=fitpilot-test"
})
@Import({SecurityConfig.class, UserAccessGuard.class})
class NutritionPlanControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private UserProfileService profiles;
    @MockitoBean
    private JwtDecoder decoder;

    private void authenticate() {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "HS256").subject("7").claim("userId", 7L).build());
    }

    @Test
    void usesAuthenticatedUserEvenIfBodyContainsAnotherUserId() throws Exception {
        authenticate();
        mvc.perform(put("/api/me/nutrition-plan")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"FAT_LOSS\",\"userId\":99}"))
                .andExpect(status().isOk());
        verify(profiles).updateNutritionPlan(7L, Goal.FAT_LOSS);
        verifyNoMoreInteractions(profiles);
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(put("/api/me/nutrition-plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"FAT_LOSS\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"goal\":null}", "{\"goal\":\"INVALID\"}"})
    void rejectsInvalidGoals(String body) throws Exception {
        authenticate();
        mvc.perform(put("/api/me/nutrition-plan")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(profiles);
    }
}
