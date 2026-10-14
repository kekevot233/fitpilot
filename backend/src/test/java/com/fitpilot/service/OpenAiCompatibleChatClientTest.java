package com.fitpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.exception.AiNutritionException;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OpenAiCompatibleChatClientTest {

    @Test
    void reportsMissingApiKeyWithoutMakingNetworkRequest() {
        HttpClient httpClient = mock(HttpClient.class);
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
                new ObjectMapper(), httpClient, "https://api.example.com/v1/", "", "test-model", 10
        );

        assertThatThrownBy(() -> client.completeJson("system", "user"))
                .isInstanceOfSatisfying(AiNutritionException.class,
                        exception -> assertThat(exception.isUnavailable()).isTrue())
                .hasMessageContaining("AI_API_KEY");
        verifyNoInteractions(httpClient);
    }
}
