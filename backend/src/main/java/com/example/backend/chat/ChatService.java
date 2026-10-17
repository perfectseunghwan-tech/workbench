package com.example.backend.chat;

import com.example.backend.trade.ApiException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
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
    private final ChatModel model;
    private final Map<String, ChatMemory> conversations = new LinkedHashMap<>(16, 0.75f, true);

    public ChatService(ChatModel model) {
        this.model = model;
    }

    public ChatDtos.ChatResponse chat(ChatDtos.ChatRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }
        String id = validateConversationId(request.conversationId());
        String message = request.message() == null ? "" : request.message().trim();
        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "질문은 1~4000자로 입력해 주세요.");
        }

        ChatMemory memory = memoryFor(id);
        synchronized (memory) {
            UserMessage userMessage = UserMessage.from(message);
            List<ChatMessage> context = new ArrayList<>(memory.messages());
            context.add(userMessage);
            try {
                AiMessage answer = model.chat(context).aiMessage();
                if (answer == null || answer.text() == null || answer.text().isBlank()) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "답변을 생성하지 못했습니다. 다시 시도해 주세요.");
                }
                memory.add(userMessage);
                memory.add(answer);
                return new ChatDtos.ChatResponse(id, answer.text(), LocalDateTime.now());
            } catch (AuthenticationException exception) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI 연결 설정을 확인해 주세요.");
            } catch (RateLimitException exception) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "요청이 많습니다. 잠시 후 다시 시도해 주세요.");
            } catch (TimeoutException exception) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "답변 시간이 초과되었습니다. 다시 시도해 주세요.");
            } catch (dev.langchain4j.exception.LangChain4jException exception) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI 서비스에 연결하지 못했습니다.");
            }
        }
    }

    public void clear(String conversationId) {
        String id = validateConversationId(conversationId);
        synchronized (conversations) {
            conversations.remove(id);
        }
    }

    private ChatMemory memoryFor(String id) {
        synchronized (conversations) {
            ChatMemory memory = conversations.computeIfAbsent(id,
                    ignored -> MessageWindowChatMemory.withMaxMessages(20));
            if (conversations.size() > MAX_CONVERSATIONS) {
                Iterator<String> oldest = conversations.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            return memory;
        }
    }

    private String validateConversationId(String id) {
        try {
            return UUID.fromString(id).toString();
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "대화 ID가 올바르지 않습니다.");
        }
    }
}
