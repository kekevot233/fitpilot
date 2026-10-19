package com.fitpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fitpilot.exception.AiNutritionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class OpenAiCompatibleChatClient implements AiChatCompletionClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    @Autowired
    public OpenAiCompatibleChatClient(
            ObjectMapper objectMapper,
            @Value("${fitpilot.ai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${fitpilot.ai.api-key:}") String apiKey,
            @Value("${fitpilot.ai.model:deepseek-flash}") String model,
            @Value("${fitpilot.ai.timeout-seconds:25}") long timeoutSeconds) {
        this(objectMapper, HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                        .build(),
                baseUrl, apiKey, model, timeoutSeconds);
    }

    OpenAiCompatibleChatClient(ObjectMapper objectMapper, HttpClient httpClient, String baseUrl,
                               String apiKey, String model, long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.endpoint = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        this.apiKey = apiKey.trim();
        this.model = model.trim();
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }

    @Override
    public String completeJson(String systemPrompt, String userPrompt) {
        if (apiKey.isBlank()) {
            throw new AiNutritionException("AI 营养估算尚未配置，请先在后端设置 AI_API_KEY。", true);
        }
        if (model.isBlank()) {
            throw new AiNutritionException("AI 模型名称尚未配置。", true);
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_tokens", 1024);
        body.putObject("response_format").put("type", "json_object");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        try {
            URI providerUri = URI.create(endpoint);
            if ("api.deepseek.com".equalsIgnoreCase(providerUri.getHost())) {
                body.putObject("thinking").put("type", "disabled");
            }
            HttpRequest request = HttpRequest.newBuilder(providerUri)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiNutritionException(
                        "大模型服务调用失败（HTTP " + response.statusCode() + "），请检查模型配置或稍后重试。",
                        false
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            if ("length".equals(root.path("choices").path(0).path("finish_reason").asText())) {
                throw new AiNutritionException("大模型输出达到长度上限，请缩短菜品描述后重试。", false);
            }
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new AiNutritionException("大模型没有返回可用的营养估算结果。", false);
            }
            return content.asText();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiNutritionException("AI 营养估算已中断，请重试。", false, exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new AiNutritionException("暂时无法连接大模型服务，请检查网络和 API 地址。", false, exception);
        }
    }
}
