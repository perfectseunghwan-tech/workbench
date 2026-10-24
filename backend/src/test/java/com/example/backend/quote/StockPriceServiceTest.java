package com.example.backend.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.backend.trade.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class StockPriceServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private TossPriceClient client;

    @InjectMocks
    private StockPriceService service;

    @Test
    void rejectsInvalidSymbolsBeforeCallingToss() {
        for (String symbol : new String[] {null, " ", "005930,AAPL", "한글", "AAPL?"}) {
            assertThatThrownBy(() -> service.find(symbol))
                    .isInstanceOf(ApiException.class)
                    .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(client, never()).prices(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void preservesLeadingZeroesAndDecimalText() throws Exception {
        when(client.prices("005930")).thenReturn(json("""
                {"result":[{"symbol":"005930","lastPrice":"72000.00","currency":"KRW",
                "timestamp":"2026-03-25T09:30:00.123+09:00"}]}
                """));

        var price = service.find(" 005930 ");

        assertThat(price.symbol()).isEqualTo("005930");
        assertThat(price.lastPrice()).isEqualTo("72000.00");
        assertThat(price.currency()).isEqualTo("KRW");
        assertThat(price.timestamp()).isEqualTo("2026-03-25T09:30:00.123+09:00");
    }

    @Test
    void allowsMissingTimestamp() throws Exception {
        when(client.prices("AAPL")).thenReturn(json("""
                {"result":[{"symbol":"AAPL","lastPrice":"185.70","currency":"USD","timestamp":null}]}
                """));
        assertThat(service.find("AAPL").timestamp()).isNull();
    }

    @Test
    void distinguishesMissingStockFromMalformedResponse() throws Exception {
        when(client.prices("AAPL")).thenReturn(json("{\"result\":[]}"));
        assertStatus("AAPL", HttpStatus.NOT_FOUND);

        when(client.prices("AAPL")).thenReturn(json("""
                {"result":[{"symbol":"AAPL","lastPrice":"invalid","currency":"USD"}]}
                """));
        assertStatus("AAPL", HttpStatus.BAD_GATEWAY);
    }

    @Test
    void collectsDailyClosesAcrossPagesWithinInclusiveDates() throws Exception {
        String firstBefore = "2026-03-27T12:00:00Z";
        when(client.dailyCandles("005930", firstBefore)).thenReturn(json("""
                {"result":{"candles":[
                  {"timestamp":"2026-03-26T00:00:00+09:00","closePrice":"72000.00","currency":"KRW"},
                  {"timestamp":"2026-03-25T00:00:00+09:00","closePrice":"71000","currency":"KRW"}
                ],"nextBefore":"2026-03-25T00:00:00+09:00"}}
                """));
        when(client.dailyCandles("005930", "2026-03-25T00:00:00+09:00")).thenReturn(json("""
                {"result":{"candles":[
                  {"timestamp":"2026-03-25T00:00:00+09:00","closePrice":"71000","currency":"KRW"},
                  {"timestamp":"2026-03-24T00:00:00+09:00","closePrice":"70000","currency":"KRW"},
                  {"timestamp":"2026-03-23T00:00:00+09:00","closePrice":"69000","currency":"KRW"}
                ],"nextBefore":null}}
                """));

        var result = service.dailyCloses(" 005930 ", "2026-03-24", "2026-03-26");

        assertThat(result.symbol()).isEqualTo("005930");
        assertThat(result.prices()).extracting(DailyClose -> DailyClose.date())
                .containsExactly("2026-03-26", "2026-03-25", "2026-03-24");
        assertThat(result.prices().get(0).closePrice()).isEqualTo("72000.00");
    }

    @Test
    void rejectsInvalidDailyRangeWithoutCallingToss() {
        for (String[] range : new String[][] {{"2026-03-26", "2026-03-25"},
                {"2026-02-30", "2026-03-25"}, {null, "2026-03-25"}}) {
            assertThatThrownBy(() -> service.dailyCloses("AAPL", range[0], range[1]))
                    .isInstanceOf(ApiException.class)
                    .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(client);
    }

    private void assertStatus(String symbol, HttpStatus status) {
        assertThatThrownBy(() -> service.find(symbol)).isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(status));
    }

    private JsonNode json(String value) throws Exception {
        return mapper.readTree(value);
    }
}
