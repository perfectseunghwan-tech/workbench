package com.example.backend.quote;

import com.example.backend.trade.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ExchangeRateService {
    private final TossPriceClient client;

    public ExchangeRateService(TossPriceClient client) {
        this.client = client;
    }

    public ExchangeRateResponse usdToKrw() {
        JsonNode response = client.exchangeRate();
        JsonNode result = response == null ? null : response.path("result");
        if (result == null || !"USD".equals(result.path("baseCurrency").asText())
                || !"KRW".equals(result.path("quoteCurrency").asText())
                || !result.path("rate").isTextual()) throw invalidResponse();
        try {
            BigDecimal rate = new BigDecimal(result.path("rate").asText());
            if (rate.signum() <= 0) throw invalidResponse();
            return new ExchangeRateResponse("USD", "KRW", rate);
        } catch (NumberFormatException exception) {
            throw invalidResponse();
        }
    }

    private ApiException invalidResponse() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "환율 응답 형식이 올바르지 않습니다.");
    }

    public record ExchangeRateResponse(String baseCurrency, String quoteCurrency, BigDecimal rate) {}
}
