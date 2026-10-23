package com.example.backend.balance;

import static com.example.backend.balance.StockBalanceDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.backend.balance.StockBalanceRepository.BalanceKey;
import com.example.backend.trade.ApiException;
import com.example.backend.trade.StockTradeDtos.TradeRequest;
import com.example.backend.trade.StockTradeDtos.TradeResponse;
import com.example.backend.trade.StockTradeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StockBalanceServiceTest {
    private StockBalanceRepository balances;
    private StockTradeRepository trades;
    private StockBalanceService service;

    @BeforeEach
    void setup() {
        balances = mock(StockBalanceRepository.class);
        trades = mock(StockTradeRepository.class);
        service = new StockBalanceService(balances, trades);
        when(balances.activeAccount(1)).thenReturn(true);
        when(balances.activeStock(10)).thenReturn(true);
    }

    @Test
    void multipleTradesApplyNetQuantityForTheAccountAndStock() {
        when(trades.findById(1)).thenReturn(Optional.of(trade(1, "BUY", "5")));
        when(trades.findById(2)).thenReturn(Optional.of(trade(2, "SELL", "2")));
        when(balances.quantity(1, 10)).thenReturn(new BigDecimal("4"));
        ApplicationRequest request = new ApplicationRequest(List.of(1L, 2L));

        List<ApplicationGroup> result = service.apply(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).expectedQuantity()).isEqualByComparingTo("7");
        verify(balances).applyDelta(1, 10, new BigDecimal("3"));
        verify(balances).saveApplication(1, 1, 10, "BUY", new BigDecimal("5"));
        verify(balances).saveApplication(2, 1, 10, "SELL", new BigDecimal("2"));
        verify(balances, times(1)).snapshot(1);
    }

    @Test
    void negativeResultDoesNotWriteBalanceOrApplication() {
        when(trades.findById(2)).thenReturn(Optional.of(trade(2, "SELL", "2")));
        when(balances.quantity(1, 10)).thenReturn(BigDecimal.ONE);

        assertThatThrownBy(() -> service.apply(new ApplicationRequest(List.of(2L))))
                .isInstanceOf(ApiException.class);

        verify(balances, never()).applyDelta(anyLong(), anyLong(), any());
        verify(balances, never()).saveApplication(anyLong(), anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void correctingAppliedTradeReversesOldEffectAndAppliesNewEffect() {
        when(balances.applied(1)).thenReturn(Optional.of(new AppliedTrade(1L, 1L, 10L, "BUY", new BigDecimal("5"))));
        when(balances.quantity(1, 10)).thenReturn(new BigDecimal("10"));
        TradeRequest changed = new TradeRequest(1L, 10L, LocalDate.of(2026, 10, 19), "SELL",
                new BigDecimal("2"), BigDecimal.TEN, null, null, null);

        service.reviseApplied(trade(1, "BUY", "5"), changed);

        verify(balances).applyDelta(1, 10, new BigDecimal("-7"));
        verify(balances).saveApplication(1, 1, 10, "SELL", new BigDecimal("2"));
        verify(balances).snapshot(1);
    }

    @Test
    void deletingBalanceClearsAppliedTradesAndSavesSnapshot() {
        when(balances.findBalanceKey(7)).thenReturn(Optional.of(new BalanceKey(1, 10)));
        when(balances.deleteBalance(7)).thenReturn(1);

        service.delete(7);

        verify(balances).lockAccounts(List.of(1L));
        verify(balances).removeApplications(1, 10);
        verify(balances).snapshot(1);
    }

    @Test
    void missingBalanceDoesNotRemoveApplicationsOrSaveSnapshot() {
        assertThatThrownBy(() -> service.delete(7)).isInstanceOf(ApiException.class);

        verify(balances, never()).removeApplications(anyLong(), anyLong());
        verify(balances, never()).snapshot(anyLong());
    }

    @Test
    void negativeAveragePurchasePriceIsRejectedBeforeSaving() {
        BalanceRequest request = new BalanceRequest(1L, 10L, BigDecimal.ONE,
                null, null, new BigDecimal("-1"), null, null, null);

        assertThatThrownBy(() -> service.save(request)).isInstanceOf(ApiException.class);

        verify(balances, never()).saveManual(any());
    }

    private TradeResponse trade(long id, String type, String quantity) {
        return new TradeResponse(id, 1L, 3L, "증권사", "계좌", 10L, "0010", "종목",
                "KRX", "KRW", LocalDate.of(2026, 10, 19), type, new BigDecimal(quantity),
                BigDecimal.TEN, BigDecimal.TEN, null, null, null, null, null, false);
    }
}
