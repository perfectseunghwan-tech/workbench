package com.example.backend.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StockTradeServiceTest {
    private final StockTradeService service = new StockTradeService(mock(StockTradeRepository.class));

    @Test
    void executionAmountIsRoundedToTwoDecimalPlaces() {
        BigDecimal amount = service.calculateExecutionAmount(
                new BigDecimal("3.125000"),
                new BigDecimal("1234.567000"));

        assertThat(amount).isEqualByComparingTo("3858.02");
    }
}
