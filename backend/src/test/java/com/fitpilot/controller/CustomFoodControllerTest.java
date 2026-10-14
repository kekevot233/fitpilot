package com.fitpilot.controller;

import com.fitpilot.config.SecurityConfig;
import com.fitpilot.dto.food.CreateFoodRequest;
import com.fitpilot.service.FoodService;
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

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = CustomFoodController.class, properties = {
        "fitpilot.security.jwt.secret=custom-food-test-secret-at-least-32-bytes",
        "fitpilot.security.jwt.issuer=fitpilot-test"
})
@Import({SecurityConfig.class, UserAccessGuard.class})
class CustomFoodControllerTest {
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
    void listsAuthenticatedUsersCustomFoods() throws Exception {
        authenticate();
        mvc.perform(get("/api/me/foods/custom")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
        verify(foodService).findCustomFoods(7L);
    }

    @Test
    void updatesAuthenticatedUsersCustomFood() throws Exception {
        authenticate();
        mvc.perform(put("/api/me/foods/custom/9")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"自制鸡胸饭","caloriesPer100g":165,"proteinPer100g":20,
                                 "carbsPer100g":10,"fatPer100g":5}
                                """))
                .andExpect(status().isOk());
        verify(foodService).updateCustomFood(7L, 9L, new CreateFoodRequest(
                "自制鸡胸饭", new BigDecimal("165"), new BigDecimal("20"),
                new BigDecimal("10"), new BigDecimal("5")));
    }

    @Test
    void archivesAuthenticatedUsersCustomFood() throws Exception {
        authenticate();
        mvc.perform(delete("/api/me/foods/custom/9")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNoContent());
        verify(foodService).archiveCustomFood(7L, 9L);
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/api/me/foods/custom"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(foodService);
    }
}
