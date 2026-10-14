package com.fitpilot.controller;

import com.fitpilot.config.SecurityConfig;
import com.fitpilot.domain.ActivityLevel;
import com.fitpilot.dto.user.UpdateUserProfileRequest;
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

@WebMvcTest(value = ProfileController.class, properties = {
        "fitpilot.security.jwt.secret=profile-test-secret-at-least-32-bytes",
        "fitpilot.security.jwt.issuer=fitpilot-test"
})
@Import({SecurityConfig.class, UserAccessGuard.class})
class ProfileControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private UserProfileService profiles;
    @MockitoBean
    private JwtDecoder decoder;

    private static final String VALID_BODY = """
            {
              "age": 23,
              "heightCm": 180,
              "weightKg": 80,
              "activityLevel": "LIGHT",
              "weeklyTrainingFrequency": 3
            }
            """;

    private void authenticate() {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "HS256").subject("7").claim("userId", 7L).build());
    }

    @Test
    void updatesAuthenticatedUsersOwnProfile() throws Exception {
        authenticate();

        mvc.perform(put("/api/me/profile")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());

        verify(profiles).updateProfile(7L,
                new UpdateUserProfileRequest(23, 180.0, 80.0, ActivityLevel.LIGHT, 3));
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(put("/api/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"age\":13,\"heightCm\":180,\"weightKg\":80,\"activityLevel\":\"LIGHT\",\"weeklyTrainingFrequency\":3}",
            "{\"age\":23,\"heightCm\":99,\"weightKg\":80,\"activityLevel\":\"LIGHT\",\"weeklyTrainingFrequency\":3}",
            "{\"age\":23,\"heightCm\":180,\"weightKg\":29,\"activityLevel\":\"LIGHT\",\"weeklyTrainingFrequency\":3}",
            "{\"age\":23,\"heightCm\":180,\"weightKg\":80,\"activityLevel\":\"LIGHT\",\"weeklyTrainingFrequency\":8}"
    })
    void rejectsInvalidProfile(String body) throws Exception {
        authenticate();
        mvc.perform(put("/api/me/profile")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(profiles);
    }
}
