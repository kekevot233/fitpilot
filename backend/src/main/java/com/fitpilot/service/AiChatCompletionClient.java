package com.fitpilot.service;

public interface AiChatCompletionClient {

    String completeJson(String systemPrompt, String userPrompt);
}
