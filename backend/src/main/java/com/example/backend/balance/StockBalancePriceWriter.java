package com.example.backend.balance;

import com.example.backend.balance.StockBalanceDtos.BalanceResponse;
import com.example.backend.trade.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockBalancePriceWriter {
    private final StockBalanceRepository balances;

    public StockBalancePriceWriter(StockBalanceRepository balances) {
        this.balances = balances;
    }

    @Transactional
    public void apply(long accountId, List<BalanceResponse> expected, Map<String, BigDecimal> prices,
            BigDecimal usdToKrwRate) {
        balances.lockAccounts(List.of(accountId));
        List<BalanceResponse> actual = balances.findBalances(null, accountId, null);
        if (actual.size() != expected.size()) throw changed();
        Map<Long, BalanceResponse> expectedById = new java.util.HashMap<>();
        for (BalanceResponse balance : expected) expectedById.put(balance.balanceId(), balance);
        for (BalanceResponse balance : actual) {
            BalanceResponse before = expectedById.remove(balance.balanceId());
            if (before == null || before.stockId().longValue() != balance.stockId().longValue()
                    || !before.stockCode().equals(balance.stockCode())
                    || !before.currencyCode().equals(balance.currencyCode())) throw changed();
        }
        if (!expectedById.isEmpty()) throw changed();
        for (BalanceResponse balance : actual) {
            if (balances.updateCurrentPrice(accountId, balance.balanceId(), balance.stockId(),
                    prices.get(balance.stockCode())) != 1) throw changed();
        }
        if (usdToKrwRate != null) balances.saveExchangeRate(accountId, usdToKrwRate);
        balances.snapshot(accountId);
    }

    private ApiException changed() {
        return new ApiException(HttpStatus.CONFLICT, "조회 중 잔고 또는 종목 정보가 변경되었습니다. 다시 시도해 주세요.");
    }
}
