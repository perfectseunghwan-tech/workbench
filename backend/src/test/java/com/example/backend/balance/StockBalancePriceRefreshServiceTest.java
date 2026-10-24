package com.example.backend.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.backend.balance.StockBalanceDtos.BalanceResponse;
import com.example.backend.balance.StockBalanceDtos.PriceRefreshRequest;
import com.example.backend.quote.StockPriceDtos.StockPriceResponse;
import com.example.backend.quote.StockPriceService;
import com.example.backend.quote.ExchangeRateService;
import com.example.backend.quote.ExchangeRateService.ExchangeRateResponse;
import com.example.backend.trade.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class StockBalancePriceRefreshServiceTest {
    private StockBalanceRepository balances;
    private StockPriceService prices;
    private StockBalancePriceWriter writer;
    private ExchangeRateService exchangeRates;
    private StockBalancePriceRefreshService service;

    @BeforeEach
    void setup() {
        balances = org.mockito.Mockito.mock(StockBalanceRepository.class);
        prices = org.mockito.Mockito.mock(StockPriceService.class);
        writer = org.mockito.Mockito.mock(StockBalancePriceWriter.class);
        exchangeRates = org.mockito.Mockito.mock(ExchangeRateService.class);
        service = new StockBalancePriceRefreshService(balances, prices, writer, exchangeRates);
        when(balances.accountExists(1)).thenReturn(true);
    }

    @Test
    void usesLatestPriceForAllBalancesAndLooksUpDuplicateSymbolOnce() {
        List<BalanceResponse> current = List.of(balance(1, "005930", "KRW"),
                balance(2, "AAPL", "USD"), balance(3, "005930", "KRW"));
        when(balances.findBalances(null, 1L, null)).thenReturn(current);
        when(prices.find("005930")).thenReturn(new StockPriceResponse("005930", "72000.00", "KRW", null));
        when(prices.find("AAPL")).thenReturn(new StockPriceResponse("AAPL", "185.70", "USD",
                "2026-10-23T16:00:00-04:00"));
        when(exchangeRates.usdToKrw()).thenReturn(new ExchangeRateResponse("USD", "KRW", new BigDecimal("1380.5")));

        var result = service.refresh(new PriceRefreshRequest(1L));

        assertThat(result.updatedCount()).isEqualTo(3);
        assertThat(result.appliedAt()).isNotNull();
        assertThat(result.usdToKrwRate()).isEqualByComparingTo("1380.5");
        verify(prices).find("005930");
        verify(prices).find("AAPL");
        verify(exchangeRates).usdToKrw();
        verify(writer).apply(eq(1L), eq(current), eq(Map.of("005930", new BigDecimal("72000.00"),
                "AAPL", new BigDecimal("185.70"))), eq(new BigDecimal("1380.5")));
    }

    @Test
    void invalidOrMissingAccountDoesNotRequestPrices() {
        assertThatThrownBy(() -> service.refresh(new PriceRefreshRequest(null)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.refresh(new PriceRefreshRequest(2L)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(prices, writer);
    }

    @Test
    void oneInvalidPricePreventsEveryWrite() {
        when(balances.findBalances(null, 1L, null)).thenReturn(List.of(
                balance(1, "005930", "KRW"), balance(2, "AAPL", "USD")));
        when(prices.find("005930")).thenReturn(new StockPriceResponse("005930", "72000", "KRW", null));
        when(prices.find("AAPL")).thenReturn(new StockPriceResponse("AAPL", "185.1234567", "USD", null));

        assertThatThrownBy(() -> service.refresh(new PriceRefreshRequest(1L)))
                .isInstanceOf(ApiException.class).hasMessageContaining("AAPL");
        verifyNoInteractions(writer);
    }

    @Test
    void currencyMismatchPreventsWrite() {
        when(balances.findBalances(null, 1L, null)).thenReturn(List.of(balance(1, "AAPL", "USD")));
        when(prices.find("AAPL")).thenReturn(new StockPriceResponse("AAPL", "185.70", "KRW", null));

        assertThatThrownBy(() -> service.refresh(new PriceRefreshRequest(1L)))
                .isInstanceOf(ApiException.class).hasMessageContaining("AAPL");
        verify(writer, never()).apply(eq(1L), any(), any(), any());
    }

    @Test
    void quoteFailureNamesTheStockAndPreventsWrite() {
        when(balances.findBalances(null, 1L, null)).thenReturn(List.of(balance(1, "AAPL", "USD")));
        when(prices.find("AAPL")).thenThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                "시세 조회 요청이 많습니다."));

        assertThatThrownBy(() -> service.refresh(new PriceRefreshRequest(1L)))
                .isInstanceOf(ApiException.class).hasMessageContaining("AAPL")
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verifyNoInteractions(writer);
    }

    @Test
    void exchangeRateFailurePreventsEveryWrite() {
        when(balances.findBalances(null, 1L, null)).thenReturn(List.of(balance(1, "AAPL", "USD")));
        when(prices.find("AAPL")).thenReturn(new StockPriceResponse("AAPL", "185.70", "USD", null));
        when(exchangeRates.usdToKrw()).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "환율 조회 실패"));

        assertThatThrownBy(() -> service.refresh(new PriceRefreshRequest(1L)))
                .isInstanceOf(ApiException.class).hasMessageContaining("환율 조회 실패");
        verifyNoInteractions(writer);
    }

    private BalanceResponse balance(long id, String symbol, String currency) {
        return new BalanceResponse(id, 1L, 1L, "증권사", "계좌", id,
                symbol, "종목" + id, currency, BigDecimal.ONE, null,
                null, null, null, null, null, null);
    }
}
