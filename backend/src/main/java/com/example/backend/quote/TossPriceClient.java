package com.example.backend.quote;

import com.example.backend.trade.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TossPriceClient {
    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private Token cachedToken;

    public TossPriceClient(@Qualifier("tossPriceRestClient") RestClient restClient,
            @Value("${TOSS_CLIENT_ID:}") String clientId,
            @Value("${TOSS_CLIENT_SECRET:}") String clientSecret) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public JsonNode prices(String symbol) {
        return authorizedRequest(accessToken -> requestPrice(symbol, accessToken));
    }

    public JsonNode exchangeRate() {
        return authorizedRequest(accessToken -> restClient.get()
                .uri(builder -> builder.path("/api/v1/exchange-rate")
                        .queryParam("baseCurrency", "USD")
                        .queryParam("quoteCurrency", "KRW").build())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve().body(JsonNode.class));
    }

    public JsonNode dailyCandles(String symbol, String before) {
        return authorizedRequest(accessToken -> restClient.get()
                .uri(builder -> builder.path("/api/v1/candles")
                        .queryParam("symbol", symbol).queryParam("interval", "1d")
                        .queryParam("count", 200).queryParam("before", "{before}").build(before))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve().body(JsonNode.class));
    }

    private JsonNode authorizedRequest(Function<String, JsonNode> request) {
        String accessToken = token();
        try {
            return request.apply(accessToken);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 401) throw priceError(exception);
            invalidate(accessToken);
            try {
                return request.apply(token());
            } catch (RestClientResponseException retryException) {
                throw priceError(retryException);
            } catch (ResourceAccessException retryException) {
                throw timeout();
            } catch (RestClientException retryException) {
                throw invalidPriceResponse();
            }
        } catch (ResourceAccessException exception) {
            throw timeout();
        } catch (RestClientException exception) {
            throw invalidPriceResponse();
        }
    }

    private JsonNode requestPrice(String symbol, String accessToken) {
        return restClient.get()
                .uri(builder -> builder.path("/api/v1/prices").queryParam("symbols", symbol).build())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve().body(JsonNode.class);
    }

    private synchronized String token() {
        if (cachedToken != null && Instant.now().isBefore(cachedToken.expiresAt())) {
            return cachedToken.value();
        }
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "서버 실행 프로세스에 토스증권 환경변수가 없습니다. 새 터미널에서 서버를 다시 실행해 주세요.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        JsonNode response;
        try {
            response = restClient.post().uri("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw switch (exception.getStatusCode().value()) {
                case 401 -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "토스증권 클라이언트 인증에 실패했습니다. Client ID와 Secret을 확인해 주세요.");
                case 403 -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "토스증권에 등록된 허용 IP에서 요청했는지 확인해 주세요.");
                case 429 -> rateLimited();
                default -> unavailable();
            };
        } catch (ResourceAccessException exception) {
            throw timeout();
        } catch (RestClientException exception) {
            throw invalidTokenResponse();
        }
        if (response == null || !response.path("access_token").isTextual()
                || response.path("access_token").asText().isBlank()
                || !"Bearer".equals(response.path("token_type").asText())
                || !response.path("expires_in").canConvertToLong()
                || response.path("expires_in").asLong() <= 0) {
            throw invalidTokenResponse();
        }
        long expiresIn = response.path("expires_in").asLong();
        long buffer = Math.min(30, Math.max(1, expiresIn / 10));
        cachedToken = new Token(response.path("access_token").asText(),
                Instant.now().plusSeconds(Math.max(1, expiresIn - buffer)));
        return cachedToken.value();
    }

    private synchronized void invalidate(String accessToken) {
        if (cachedToken != null && cachedToken.value().equals(accessToken)) cachedToken = null;
    }

    private ApiException priceError(RestClientResponseException exception) {
        return switch (exception.getStatusCode().value()) {
            case 401 -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "토스증권 시세 API 인증에 실패했습니다. Client ID와 Secret을 확인해 주세요.");
            case 403 -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "토스증권 시세 API 접근 권한 또는 허용 IP를 확인해 주세요.");
            case 404 -> new ApiException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다.");
            case 429 -> rateLimited();
            default -> unavailable();
        };
    }

    private ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "시세 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
    }

    private ApiException timeout() {
        return new ApiException(HttpStatus.GATEWAY_TIMEOUT, "토스증권 시세 조회 연결 시간이 초과되었습니다.");
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "토스증권 시세 서비스를 이용할 수 없습니다.");
    }

    private ApiException invalidPriceResponse() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "시세 응답 형식이 올바르지 않습니다.");
    }

    private ApiException invalidTokenResponse() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "토스증권 인증 응답 형식이 올바르지 않습니다.");
    }

    private record Token(String value, Instant expiresAt) {}
}
