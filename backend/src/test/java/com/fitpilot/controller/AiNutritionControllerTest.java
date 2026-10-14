package com.fitpilot.controller;

import com.fitpilot.config.SecurityConfig;
import com.fitpilot.service.AiNutritionEstimateService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AiNutritionController.class, properties = {
        "fitpilot.security.jwt.secret=ai-nutrition-test-secret-at-least-32-bytes",
        "fitpilot.security.jwt.issuer=fitpilot-test"
})
@Import(SecurityConfig.class)
class AiNutritionControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private AiNutritionEstimateService service;
    @MockitoBean
    private JwtDecoder decoder;

    private void authenticate() {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "HS256").subject("7").claim("userId", 7L).build());
    }

    @Test
    void allowsAuthenticatedEstimate() throws Exception {
        authenticate();
        mvc.perform(post("/api/me/ai-nutrition/estimate")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dishName\":\"宫保鸡丁\",\"grams\":250}"))
                .andExpect(status().isOk());

        verify(service).estimate(org.mockito.ArgumentMatchers.argThat(request ->
                request.dishName().equals("宫保鸡丁") && request.grams().compareTo(new java.math.BigDecimal("250")) == 0));
    }

    @Test
    void rejectsUnauthenticatedEstimate() throws Exception {
        mvc.perform(post("/api/me/ai-nutrition/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dishName\":\"宫保鸡丁\",\"grams\":250}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"dishName\":\"\",\"grams\":250}",
            "{\"dishName\":\"宫保鸡丁\",\"grams\":0}",
            "{\"dishName\":\"宫保鸡丁\",\"grams\":5001}"
    })
    void rejectsInvalidInput(String body) throws Exception {
        authenticate();
        mvc.perform(post("/api/me/ai-nutrition/estimate")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
