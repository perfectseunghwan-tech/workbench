package com.example.backend.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.trade.ApiException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class ChatServiceTest {
    private final ChatModel model = mock(ChatModel.class);
    private final ChatService service = new ChatService(model);

    @Test
    void includesPreviousTurnOnlyWithinTheSameConversation() {
        ChatResponse response = mock(ChatResponse.class);
        when(response.aiMessage()).thenReturn(AiMessage.from("답변"));
        when(model.chat(anyList())).thenReturn(response);
        String firstId = UUID.randomUUID().toString();
        String secondId = UUID.randomUUID().toString();

        service.chat(new ChatDtos.ChatRequest(firstId, "첫 질문"));
        service.chat(new ChatDtos.ChatRequest(firstId, "후속 질문"));
        service.chat(new ChatDtos.ChatRequest(secondId, "다른 대화"));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<ChatMessage>> contexts = ArgumentCaptor.forClass((Class) List.class);
        verify(model, times(3)).chat(contexts.capture());
        assertThat(contexts.getAllValues()).extracting(List::size).containsExactly(1, 3, 1);
    }

    @Test
    void clearingConversationRemovesItsContext() {
        ChatResponse response = mock(ChatResponse.class);
        when(response.aiMessage()).thenReturn(AiMessage.from("답변"));
        when(model.chat(anyList())).thenReturn(response);
        String id = UUID.randomUUID().toString();

        service.chat(new ChatDtos.ChatRequest(id, "첫 질문"));
        service.clear(id);
        service.chat(new ChatDtos.ChatRequest(id, "새 질문"));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<ChatMessage>> contexts = ArgumentCaptor.forClass((Class) List.class);
        verify(model, times(2)).chat(contexts.capture());
        assertThat(contexts.getAllValues()).extracting(List::size).containsExactly(1, 1);
    }

    @Test
    void rejectsBlankQuestionBeforeCallingModel() {
        String id = UUID.randomUUID().toString();
        assertThatThrownBy(() -> service.chat(new ChatDtos.ChatRequest(id, "  ")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("질문은");
    }

    @Test
    void failedRequestIsNotAddedToConversation() {
        ChatResponse response = mock(ChatResponse.class);
        when(response.aiMessage()).thenReturn(AiMessage.from("답변"));
        when(model.chat(anyList())).thenThrow(mock(RateLimitException.class))
                .thenReturn(response);
        String id = UUID.randomUUID().toString();

        assertThatThrownBy(() -> service.chat(new ChatDtos.ChatRequest(id, "다시 보낼 질문")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(service.chat(new ChatDtos.ChatRequest(id, "다시 보낼 질문")).answer())
                .isEqualTo("답변");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<ChatMessage>> contexts = ArgumentCaptor.forClass((Class) List.class);
        verify(model, times(2)).chat(contexts.capture());
        assertThat(contexts.getAllValues()).extracting(List::size).containsExactly(1, 1);
    }
}
