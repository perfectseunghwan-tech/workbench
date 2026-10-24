package com.example.backend.balance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.balance.StockBalanceDtos.BalanceResponse;
import com.example.backend.trade.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StockBalancePriceWriterTest {
    private StockBalanceRepository balances;
    private StockBalancePriceWriter writer;
    private BalanceResponse original;

    @BeforeEach
    void setup() {
        balances = org.mockito.Mockito.mock(StockBalanceRepository.class);
        writer = new StockBalancePriceWriter(balances);
        original = balance("005930");
    }

    @Test
    void updatesPriceAndTakesOneAccountSnapshot() {
        when(balances.findBalances(null, 1L, null)).thenReturn(List.of(original));
        when(balances.updateCurrentPrice(1, 7, 10, new BigDecimal("72000"))).thenReturn(1);

        writer.apply(1, List.of(original), Map.of("005930", new BigDecimal("72000")), null);

        verify(balances).lockAccounts(List.of(1L));
        verify(balances).updateCurrentPrice(1, 7, 10, new BigDecimal("72000"));
        verify(balances).snapshot(1);
    }

    @Test
    void savesExchangeRateWithPricesAndSnapshot() {
        when(balances.findBalances(null, 1L, null)).thenReturn(List.of(original));
        when(balances.updateCurrentPrice(1, 7, 10, new BigDecimal("72000"))).thenReturn(1);

        writer.apply(1, List.of(original), Map.of("005930", new BigDecimal("72000")),
                new BigDecimal("1380.5"));

        verify(balances).saveExchangeRate(1, new BigDecimal("1380.5"));
        verify(balances).snapshot(1);
    }

    @Test
    void rejectsChangedSymbolBeforeWriting() {
        when(balances.findBalances(null, 1L, null)).thenReturn(List.of(balance("000660")));

        assertThatThrownBy(() -> writer.apply(1, List.of(original),
                Map.of("005930", new BigDecimal("72000")), null))
                .isInstanceOf(ApiException.class).hasMessageContaining("변경되었습니다");

        verify(balances, never()).updateCurrentPrice(anyLong(), anyLong(), anyLong(), any());
        verify(balances, never()).snapshot(anyLong());
    }

    @Test
    void failedUpdateDoesNotCreateSnapshot() {
        when(balances.findBalances(null, 1L, null)).thenReturn(List.of(original));

        assertThatThrownBy(() -> writer.apply(1, List.of(original),
                Map.of("005930", new BigDecimal("72000")), null))
                .isInstanceOf(ApiException.class);

        verify(balances, never()).snapshot(anyLong());
    }

    private BalanceResponse balance(String symbol) {
        return new BalanceResponse(7L, 1L, 1L, "증권사", "계좌", 10L,
                symbol, "종목", "KRW", BigDecimal.ONE, null,
                null, null, null, null, null, null);
    }
}
