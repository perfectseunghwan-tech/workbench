package com.example.backend.balance;

import com.example.backend.balance.StockBalanceDtos.BalanceResponse;
import com.example.backend.balance.StockBalanceDtos.PriceRefreshRequest;
import com.example.backend.balance.StockBalanceDtos.PriceRefreshResponse;
import com.example.backend.quote.StockPriceDtos.StockPriceResponse;
import com.example.backend.quote.ExchangeRateService;
import com.example.backend.quote.StockPriceService;
import com.example.backend.trade.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class StockBalancePriceRefreshService {
    private final StockBalanceRepository balances;
    private final StockPriceService prices;
    private final StockBalancePriceWriter writer;
    private final ExchangeRateService exchangeRates;

    public StockBalancePriceRefreshService(StockBalanceRepository balances, StockPriceService prices,
            StockBalancePriceWriter writer, ExchangeRateService exchangeRates) {
        this.balances = balances;
        this.prices = prices;
        this.writer = writer;
        this.exchangeRates = exchangeRates;
    }

    public PriceRefreshResponse refresh(PriceRefreshRequest request) {
        if (request == null || request.accountId() == null || request.accountId() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "계좌를 선택해 주세요.");
        }
        long accountId = request.accountId();
        if (!balances.accountExists(accountId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다.");
        }
        List<BalanceResponse> current = balances.findBalances(null, accountId, null);
        if (current.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "반영할 잔고가 없습니다.");
        }

        Map<String, BigDecimal> bySymbol = new LinkedHashMap<>();
        Map<String, StockPriceResponse> quotes = new LinkedHashMap<>();
        for (BalanceResponse balance : current) {
            StockPriceResponse quote = quotes.get(balance.stockCode());
            if (quote == null) {
                try {
                    quote = prices.find(balance.stockCode());
                } catch (ApiException exception) {
                    throw new ApiException(exception.getStatus(),
                            balance.stockName() + " (" + balance.stockCode() + "): " + exception.getMessage());
                }
                BigDecimal price;
                try {
                    price = new BigDecimal(quote.lastPrice());
                } catch (NumberFormatException exception) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY,
                            balance.stockName() + " (" + balance.stockCode() + "): 가격 형식이 올바르지 않습니다.");
                }
                if (price.signum() < 0 || price.scale() > 6 || price.precision() - (long) price.scale() > 14) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY,
                            balance.stockName() + " (" + balance.stockCode() + "): 가격 범위가 올바르지 않습니다.");
                }
                quotes.put(balance.stockCode(), quote);
                bySymbol.put(balance.stockCode(), price);
            }
            if (!quote.currency().equalsIgnoreCase(balance.currencyCode())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        balance.stockName() + " (" + balance.stockCode() + "): 시세 통화가 종목 통화와 다릅니다.");
            }
        }

        BigDecimal usdToKrwRate = current.stream().anyMatch(balance -> "USD".equals(balance.currencyCode()))
                ? exchangeRates.usdToKrw().rate() : null;
        writer.apply(accountId, current, bySymbol, usdToKrwRate);
        return new PriceRefreshResponse(current.size(), Instant.now(), usdToKrwRate);
    }

    public BigDecimal savedExchangeRate(long accountId) {
        if (accountId <= 0 || !balances.accountExists(accountId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다.");
        }
        return balances.exchangeRate(accountId);
    }
}
