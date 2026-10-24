package com.example.backend.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.backend.trade.ApiException;
import java.util.concurrent.CountDownLatch;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossPriceClientTest {
    private MockRestServiceServer server;
    private TossPriceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://openapi.tossinvest.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossPriceClient(builder.build(), "client-id", "client-secret");
    }

    @Test
    void issuesFormTokenAndReusesItForPriceRequests() {
        server.expect(requestTo("https://openapi.tossinvest.com/oauth2/token"))
                .andExpect(method(POST))
                .andExpect(request -> assertThat(request.getHeaders().getContentType()
                        .isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)).isTrue())
                .andExpect(content().string(Matchers.containsString("grant_type=client_credentials")))
                .andExpect(content().string(Matchers.containsString("client_id=client-id")))
                .andExpect(content().string(Matchers.containsString("client_secret=client-secret")))
                .andRespond(withSuccess("""
                        {"access_token":"first-token","token_type":"Bearer","expires_in":3600}
                        """, MediaType.APPLICATION_JSON));
        expectPrice("005930", "first-token");
        expectPrice("AAPL", "first-token");

        assertThat(client.prices("005930").path("result").get(0).path("symbol").asText()).isEqualTo("005930");
        assertThat(client.prices("AAPL").path("result").get(0).path("symbol").asText()).isEqualTo("AAPL");
        server.verify();
    }

    @Test
    void refreshesTokenOnceAfterUnauthorizedResponse() {
        expectToken("first-token");
        server.expect(requestTo("https://openapi.tossinvest.com/api/v1/prices?symbols=AAPL"))
                .andExpect(method(GET)).andExpect(header("Authorization", "Bearer first-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken("second-token");
        expectPrice("AAPL", "second-token");

        assertThat(client.prices("AAPL").path("result").get(0).path("symbol").asText()).isEqualTo("AAPL");
        server.verify();
    }

    @Test
    void concurrentRequestsShareOneToken() throws Exception {
        expectToken("shared-token");
        server.expect(times(2), requestTo("https://openapi.tossinvest.com/api/v1/prices?symbols=AAPL"))
                .andExpect(method(GET)).andExpect(header("Authorization", "Bearer shared-token"))
                .andRespond(withSuccess("""
                        {"result":[{"symbol":"AAPL","lastPrice":"1","currency":"USD"}]}
                        """, MediaType.APPLICATION_JSON));
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return client.prices("AAPL");
            });
            var second = executor.submit(() -> {
                start.await();
                return client.prices("AAPL");
            });
            start.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).path("result").size()).isEqualTo(1);
            assertThat(second.get(5, TimeUnit.SECONDS).path("result").size()).isEqualTo(1);
            server.verify();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsMalformedSuccessJsonAsBadGateway() {
        expectToken("first-token");
        server.expect(requestTo("https://openapi.tossinvest.com/api/v1/prices?symbols=AAPL"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertStatus(HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    @Test
    void mapsRateLimitAndServerFailure() {
        expectToken("first-token");
        server.expect(requestTo("https://openapi.tossinvest.com/api/v1/prices?symbols=AAPL"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://openapi.tossinvest.com/api/v1/prices?symbols=AAPL"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void explainsMissingProcessEnvironment() {
        TossPriceClient unconfigured = new TossPriceClient(RestClient.builder().build(), "", "");
        assertThatThrownBy(() -> unconfigured.prices("AAPL"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("서버 실행 프로세스에 토스증권 환경변수가 없습니다");
    }

    @Test
    void requestsDailyCandlesWithEncodedCursorAndBearerToken() {
        expectToken("first-token");
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/api/v1/candles");
                    assertThat(URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8))
                            .contains("symbol=005930", "interval=1d", "count=200",
                                    "before=2026-03-25T00:00:00+09:00");
                })
                .andExpect(method(GET)).andExpect(header("Authorization", "Bearer first-token"))
                .andRespond(withSuccess("""
                        {"result":{"candles":[],"nextBefore":null}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.dailyCandles("005930", "2026-03-25T00:00:00+09:00")
                .path("result").path("candles").isArray()).isTrue();
        server.verify();
    }

    @Test
    void distinguishesInvalidCredentialsFromBlockedIp() {
        server.expect(requestTo("https://openapi.tossinvest.com/oauth2/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("https://openapi.tossinvest.com/oauth2/token"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.prices("AAPL"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Client ID와 Secret");
        assertThatThrownBy(() -> client.prices("AAPL"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("허용 IP");
        server.verify();
    }

    private void expectToken(String value) {
        server.expect(requestTo("https://openapi.tossinvest.com/oauth2/token"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"access_token":"%s","token_type":"Bearer","expires_in":3600}
                        """.formatted(value), MediaType.APPLICATION_JSON));
    }

    private void expectPrice(String symbol, String token) {
        server.expect(requestTo("https://openapi.tossinvest.com/api/v1/prices?symbols=" + symbol))
                .andExpect(method(GET)).andExpect(header("Authorization", "Bearer " + token))
                .andRespond(withSuccess("""
                        {"result":[{"symbol":"%s","lastPrice":"1","currency":"USD"}]}
                        """.formatted(symbol), MediaType.APPLICATION_JSON));
    }

    private void assertStatus(HttpStatus status) {
        assertThatThrownBy(() -> client.prices("AAPL")).isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(status));
    }
}
