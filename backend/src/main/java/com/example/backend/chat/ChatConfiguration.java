package com.example.backend.chat;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfiguration {
    @Bean
    ChatModel chatModel(
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${app.ai.chat.model}") String modelName,
            @Value("${app.ai.chat.timeout-seconds}") long timeoutSeconds) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY 환경변수가 설정되지 않았습니다.");
        }
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
