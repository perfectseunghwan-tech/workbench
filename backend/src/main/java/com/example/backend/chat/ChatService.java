package com.example.backend.chat;

import com.example.backend.chat.OpenAiResponsesClient.ChatTurn;
import com.example.backend.trade.ApiException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_CONVERSATIONS = 200;
    private static final int MAX_TURNS = 20;
    private final OpenAiResponsesClient client;
    private final Map<String, List<ChatTurn>> conversations = new LinkedHashMap<>(16, 0.75f, true);

    public ChatService(OpenAiResponsesClient client) {
        this.client = client;
    }

    public ChatDtos.ChatResponse chat(ChatDtos.ChatRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }
        String id = validateConversationId(request.conversationId());
        String message = clean(request.message());
        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "질문은 1~4000자로 입력해 주세요.");
        }
        String mode = request.mode() == null ? "GENERAL" : request.mode();
        if (!mode.equals("GENERAL") && !mode.equals("STOCK_ANALYSIS")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "채팅 모드가 올바르지 않습니다.");
        }
        boolean investmentMode = mode.equals("STOCK_ANALYSIS");
        String key = id + ":" + mode;
        List<ChatTurn> history = historyFor(key);
        synchronized (history) {
            List<ChatTurn> context = new ArrayList<>(history);
            context.add(new ChatTurn("user", message));
            String answer = client.answer(investmentMode, context);
            if (answer == null || answer.isBlank()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "답변을 생성하지 못했습니다. 다시 시도해 주세요.");
            }
            history.add(new ChatTurn("user", message));
            history.add(new ChatTurn("assistant", answer));
            while (history.size() > MAX_TURNS) history.remove(0);
            return new ChatDtos.ChatResponse(id, answer, LocalDateTime.now());
        }
    }

    public void clear(String conversationId) {
        String id = validateConversationId(conversationId);
        synchronized (conversations) {
            conversations.remove(id + ":GENERAL");
            conversations.remove(id + ":STOCK_ANALYSIS");
        }
    }

    private List<ChatTurn> historyFor(String key) {
        synchronized (conversations) {
            List<ChatTurn> history = conversations.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (conversations.size() > MAX_CONVERSATIONS) {
                Iterator<String> oldest = conversations.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            return history;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String validateConversationId(String id) {
        try {
            return UUID.fromString(id).toString();
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "대화 ID가 올바르지 않습니다.");
        }
    }
}
