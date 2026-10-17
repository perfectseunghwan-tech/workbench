package com.example.backend.chat;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/chat")
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping
    public ChatDtos.ChatResponse chat(@RequestBody ChatDtos.ChatRequest request) {
        return service.chat(request);
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> clear(@PathVariable String conversationId) {
        service.clear(conversationId);
        return ResponseEntity.noContent().build();
    }
}
