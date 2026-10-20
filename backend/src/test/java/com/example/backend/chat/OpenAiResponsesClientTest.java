package com.example.backend.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.backend.trade.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiResponsesClientTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractsOnlyCompletedOutputTextMessages() throws Exception {
        JsonNode response = mapper.readTree("""
                {"status":"completed","output":[
                  {"type":"reasoning","content":[]},
                  {"type":"message","content":[{"type":"output_text","text":"첫 문단"}]},
                  {"type":"message","content":[{"type":"output_text","text":"둘째 문단"}]}
                ]}
                """);
        assertThat(OpenAiResponsesClient.extractText(response)).isEqualTo("첫 문단\n둘째 문단");
    }

    @Test
    void rejectsIncompleteOrEmptyResponses() throws Exception {
        JsonNode incomplete = mapper.readTree("""
                {"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[]}
                """);
        JsonNode empty = mapper.readTree("{\"status\":\"completed\",\"output\":[]}");
        assertThatThrownBy(() -> OpenAiResponsesClient.extractText(incomplete)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> OpenAiResponsesClient.extractText(empty)).isInstanceOf(ApiException.class);
    }

    @Test
    void sendsInvestmentInstructionsAndQuestionAsSeparateResponsesFields() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"model\":\"gpt-6-luna\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"instructions\":\"# 역할")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"effort\":\"medium\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"max_output_tokens\":4000")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"store\":false")))
                .andRespond(withSuccess("""
                        {"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"분석"}]}]}
                        """, MediaType.APPLICATION_JSON));
        OpenAiResponsesClient client = new OpenAiResponsesClient(builder.build(), mapper,
                "gpt-6-luna", "medium", 4000);
        assertThat(client.answer(true, List.of(new OpenAiResponsesClient.ChatTurn("user", "질문"))))
                .isEqualTo("분석");
        server.verify();
    }
}
