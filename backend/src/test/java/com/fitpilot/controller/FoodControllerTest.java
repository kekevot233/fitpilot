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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = FoodController.class, properties = {
        "fitpilot.security.jwt.secret=food-controller-test-secret-at-least-32-bytes",
        "fitpilot.security.jwt.issuer=fitpilot-test"
})
@Import({SecurityConfig.class, UserAccessGuard.class})
class FoodControllerTest {
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
    void searchesFoodsVisibleToAuthenticatedUser() throws Exception {
        authenticate();
        mvc.perform(get("/api/foods?keyword=rice")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
        verify(foodService).findAll(7L, "rice");
    }

    @Test
    void createsFoodForAuthenticatedUser() throws Exception {
        authenticate();
        mvc.perform(post("/api/foods")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"我的鸡胸饭","caloriesPer100g":155,"proteinPer100g":18,
                                 "carbsPer100g":12,"fatPer100g":4}
                                """))
                .andExpect(status().isCreated());
        verify(foodService).create(7L, new CreateFoodRequest(
                "我的鸡胸饭", new BigDecimal("155"), new BigDecimal("18"),
                new BigDecimal("12"), new BigDecimal("4")));
    }

    @Test
    void rejectsUnauthenticatedFoodSearch() throws Exception {
        mvc.perform(get("/api/foods"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(foodService);
    }
}
