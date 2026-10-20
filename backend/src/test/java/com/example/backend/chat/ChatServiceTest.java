package com.example.backend.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.chat.OpenAiResponsesClient.ChatTurn;
import com.example.backend.trade.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class ChatServiceTest {
    private final OpenAiResponsesClient client = mock(OpenAiResponsesClient.class);
    private final ChatService service = new ChatService(client);

    @Test
    void includesPreviousTurnOnlyWithinTheSameConversationAndMode() {
        when(client.answer(eq(false), anyList())).thenReturn("일반 답변");
        when(client.answer(eq(true), anyList())).thenReturn("분석 답변");
        String id = UUID.randomUUID().toString();

        service.chat(new ChatDtos.ChatRequest(id, "첫 질문"));
        service.chat(new ChatDtos.ChatRequest(id, "후속 질문"));
        service.chat(new ChatDtos.ChatRequest(id, "종목 질문", "STOCK_ANALYSIS"));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<ChatTurn>> contexts = ArgumentCaptor.forClass((Class) List.class);
        verify(client, times(2)).answer(eq(false), contexts.capture());
        assertThat(contexts.getAllValues()).extracting(List::size).containsExactly(1, 3);
        verify(client).answer(eq(true), contexts.capture());
        assertThat(contexts.getAllValues().get(2)).hasSize(1);
    }

    @Test
    void clearingConversationRemovesItsContext() {
        when(client.answer(eq(false), anyList())).thenReturn("답변");
        String id = UUID.randomUUID().toString();

        service.chat(new ChatDtos.ChatRequest(id, "첫 질문"));
        service.clear(id);
        service.chat(new ChatDtos.ChatRequest(id, "새 질문"));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<ChatTurn>> contexts = ArgumentCaptor.forClass((Class) List.class);
        verify(client, times(2)).answer(eq(false), contexts.capture());
        assertThat(contexts.getAllValues()).extracting(List::size).containsExactly(1, 1);
    }

    @Test
    void rejectsBlankQuestionBeforeCallingClient() {
        String id = UUID.randomUUID().toString();
        assertThatThrownBy(() -> service.chat(new ChatDtos.ChatRequest(id, "  ")))
                .isInstanceOf(ApiException.class).hasMessageContaining("질문은");
    }

    @Test
    void failedRequestIsNotAddedToConversation() {
        when(client.answer(eq(false), anyList()))
                .thenThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "요청이 많습니다."))
                .thenReturn("답변");
        String id = UUID.randomUUID().toString();

        assertThatThrownBy(() -> service.chat(new ChatDtos.ChatRequest(id, "다시 보낼 질문")))
                .isInstanceOf(ApiException.class);
        assertThat(service.chat(new ChatDtos.ChatRequest(id, "다시 보낼 질문")).answer())
                .isEqualTo("답변");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<ChatTurn>> contexts = ArgumentCaptor.forClass((Class) List.class);
        verify(client, times(2)).answer(eq(false), contexts.capture());
        assertThat(contexts.getAllValues()).extracting(List::size).containsExactly(1, 1);
    }

    @Test
    void passesStockAnalysisQuestionWithoutExtraMarketData() {
        when(client.answer(eq(true), anyList())).thenReturn("조건부 전망");
        String id = UUID.randomUUID().toString();
        String answer = service.chat(new ChatDtos.ChatRequest(id, "한 달 전망", "STOCK_ANALYSIS")).answer();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<ChatTurn>> contexts = ArgumentCaptor.forClass((Class) List.class);
        verify(client).answer(eq(true), contexts.capture());
        assertThat(contexts.getValue()).containsExactly(new ChatTurn("user", "한 달 전망"));
        assertThat(answer).isEqualTo("조건부 전망");
    }

    @Test
    void rejectsUnknownMode() {
        String id = UUID.randomUUID().toString();
        assertThatThrownBy(() -> service.chat(new ChatDtos.ChatRequest(id, "분석", "UNKNOWN")))
                .isInstanceOf(ApiException.class).hasMessageContaining("채팅 모드");
    }
}
