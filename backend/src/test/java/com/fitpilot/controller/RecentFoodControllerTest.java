package com.fitpilot.controller;

import com.fitpilot.config.SecurityConfig;
import com.fitpilot.domain.MealType;
import com.fitpilot.dto.food.RecentFoodResponse;
import com.fitpilot.service.FoodService;
import com.fitpilot.service.UserAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(value = RecentFoodController.class, properties = {
        "fitpilot.security.jwt.secret=recent-food-test-secret-at-least-32-bytes",
        "fitpilot.security.jwt.issuer=fitpilot-test"
})
@Import({SecurityConfig.class, UserAccessGuard.class})
class RecentFoodControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private FoodService foodService;
    @MockitoBean
    private JwtDecoder decoder;

    private void authenticate() {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "HS256").subject("7").claim("userId", 7L).build());
    }

    @Test
    void returnsOnlyTheAuthenticatedUserRecentFoods() throws Exception {
        authenticate();
        when(foodService.findRecentlyLoggedFoods(7L, 4)).thenReturn(List.of(
                new RecentFoodResponse(
                        11L, "熟米饭", new BigDecimal("130"), new BigDecimal("2.7"),
                        new BigDecimal("28.2"), new BigDecimal("0.3"),
                        new BigDecimal("200"), MealType.LUNCH,
                        LocalDateTime.of(2026, 10, 15, 12, 30)
                )
        ));

        mvc.perform(get("/api/me/foods/recent?limit=4")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("熟米饭"))
                .andExpect(jsonPath("$[0].lastGrams").value(200))
                .andExpect(jsonPath("$[0].lastMealType").value("LUNCH"))
                .andExpect(jsonPath("$[0].lastEatenAt").value("2026-10-15T12:30:00"));

        verify(foodService).findRecentlyLoggedFoods(7L, 4);
    }

    @Test
    void rejectsAnUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/api/me/foods/recent"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(foodService);
    }

    @Test
    void rejectsAnInvalidLimit() throws Exception {
        authenticate();

        mvc.perform(get("/api/me/foods/recent?limit=13")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(foodService);
    }
}
