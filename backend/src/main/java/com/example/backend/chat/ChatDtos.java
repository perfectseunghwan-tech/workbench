package com.example.backend.chat;

import java.time.LocalDateTime;

public final class ChatDtos {
    private ChatDtos() {}

    public record ChatRequest(String conversationId, String message) {}

    public record ChatResponse(String conversationId, String answer, LocalDateTime createdAt) {}
}
