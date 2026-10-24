package com.example.backend.quote;

public final class StockPriceDtos {
    private StockPriceDtos() {}

    public record StockPriceResponse(String symbol, String lastPrice, String currency, String timestamp) {}

    public record DailyClose(String date, String closePrice, String currency) {}

    public record DailyCloseResponse(String symbol, java.util.List<DailyClose> prices) {}
}
