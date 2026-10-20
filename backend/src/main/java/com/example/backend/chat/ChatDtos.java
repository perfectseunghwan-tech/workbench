package com.example.backend.chat;

import java.time.LocalDateTime;

public final class ChatDtos {
    private ChatDtos() {}

    public record ChatRequest(String conversationId, String message, String mode) {
        public ChatRequest(String conversationId, String message) {
            this(conversationId, message, null);
        }
    }

    public record ChatResponse(String conversationId, String answer, LocalDateTime createdAt) {}
}
