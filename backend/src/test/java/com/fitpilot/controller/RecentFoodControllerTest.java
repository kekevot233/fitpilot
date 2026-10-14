package com.fitpilot.controller;

import com.fitpilot.config.SecurityConfig;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

        mvc.perform(get("/api/me/foods/recent?limit=4")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());

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
