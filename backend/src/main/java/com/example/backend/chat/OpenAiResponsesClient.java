package com.example.backend.chat;

import com.example.backend.trade.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenAiResponsesClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesClient.class);
    private static final String GENERAL_INSTRUCTIONS = "사용자의 질문에 정확하고 명확하게 한국어로 답하세요.";
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String reasoningEffort;
    private final int maxOutputTokens;
    private final String investmentInstructions;

    public OpenAiResponsesClient(@Qualifier("openAiRestClient") RestClient restClient, ObjectMapper objectMapper,
            @Value("${app.ai.chat.model}") String model,
            @Value("${app.ai.chat.reasoning-effort}") String reasoningEffort,
            @Value("${app.ai.chat.max-output-tokens}") int maxOutputTokens) throws IOException {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.maxOutputTokens = maxOutputTokens;
        this.investmentInstructions = new ClassPathResource("prompts/investment-analysis.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    public String answer(boolean investmentMode, List<ChatTurn> turns) {
        List<Map<String, String>> input = turns.stream()
                .map(turn -> Map.of("role", turn.role(), "content", turn.content()))
                .toList();
        Map<String, Object> request = Map.of(
                "model", model,
                "instructions", investmentMode ? investmentInstructions : GENERAL_INSTRUCTIONS,
                "input", input,
                "reasoning", Map.of("effort", reasoningEffort),
                "max_output_tokens", maxOutputTokens,
                "store", false);

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                ResponseEntity<JsonNode> response = restClient.post().uri("/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request).retrieve().toEntity(JsonNode.class);
                return extractText(response.getBody());
            } catch (RestClientResponseException exception) {
                String requestId = exception.getResponseHeaders() == null ? null
                        : exception.getResponseHeaders().getFirst("x-request-id");
                log.warn("OpenAI request failed: status={}, requestId={}",
                        exception.getStatusCode().value(), requestId);
                int status = exception.getStatusCode().value();
                String code = errorCode(exception.getResponseBodyAsString());
                boolean retryable = status >= 500 || status == 429 &&
                        !List.of("credit_balance_exhausted", "organization_spend_limit_exceeded",
                                "project_spend_limit_exceeded", "organization_usage_limit_exceeded")
                                .contains(code);
                if (retryable && attempt < 2) {
                    String retryAfter = exception.getResponseHeaders() == null ? null
                            : exception.getResponseHeaders().getFirst("Retry-After");
                    if (pause(attempt, retryAfter)) continue;
                }
                if (status == 429) {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                            retryable ? "요청이 많습니다. 잠시 후 다시 시도해 주세요."
                                    : "OpenAI 사용 한도를 확인해 주세요.");
                }
                if (status == 401 || status == 403) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI 연결 설정을 확인해 주세요.");
                }
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI 서비스에 연결하지 못했습니다.");
            } catch (ResourceAccessException exception) {
                log.warn("OpenAI connection failed: {}", exception.getClass().getSimpleName());
                if (attempt < 2) {
                    pause(attempt, null);
                    continue;
                }
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "답변 시간이 초과되었거나 연결에 실패했습니다.");
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI 서비스에 연결하지 못했습니다.");
    }

    static String extractText(JsonNode response) {
        if (response == null || !"completed".equals(response.path("status").asText())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "답변이 완료되지 않았습니다. 다시 시도해 주세요.");
        }
        List<String> texts = new ArrayList<>();
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asText())) continue;
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())
                        && !content.path("text").asText().isBlank()) {
                    texts.add(content.path("text").asText());
                }
            }
        }
        if (texts.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "답변 텍스트를 받지 못했습니다. 다시 시도해 주세요.");
        }
        return String.join("\n", texts);
    }

    private String errorCode(String responseBody) {
        try {
            return objectMapper.readTree(responseBody).path("error").path("code").asText();
        } catch (IOException exception) {
            return "";
        }
    }

    private boolean pause(int attempt, String retryAfter) {
        long delay = 300L * (1L << attempt);
        try {
            if (retryAfter != null) delay = Math.max(delay, Long.parseLong(retryAfter) * 1000L);
        } catch (NumberFormatException ignored) {
            // Use bounded backoff when Retry-After is not an integer number of seconds.
        }
        if (delay > 2000L) return false;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "요청이 중단되었습니다.");
        }
        return true;
    }

    public record ChatTurn(String role, String content) {}
}
