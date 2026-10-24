package com.example.backend.quote;

import com.example.backend.quote.StockPriceDtos.StockPriceResponse;
import com.example.backend.quote.StockPriceDtos.DailyClose;
import com.example.backend.quote.StockPriceDtos.DailyCloseResponse;
import com.example.backend.trade.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class StockPriceService {
    private static final Pattern SYMBOL = Pattern.compile("[A-Za-z0-9.\\-]+");
    private final TossPriceClient client;

    public StockPriceService(TossPriceClient client) {
        this.client = client;
    }

    public StockPriceResponse find(String input) {
        String symbol = validateSymbol(input);
        JsonNode response = client.prices(symbol);
        JsonNode result = response == null ? null : response.get("result");
        if (result == null || !result.isArray()) throw invalidResponse();
        for (JsonNode price : result) {
            if (price == null || !price.isObject()) throw invalidResponse();
            JsonNode returnedSymbol = price.get("symbol");
            if (returnedSymbol != null && returnedSymbol.isTextual()
                    && symbol.equalsIgnoreCase(returnedSymbol.textValue())) {
                return mapPrice(price);
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다.");
    }

    public DailyCloseResponse dailyCloses(String input, String fromInput, String toInput) {
        String symbol = validateSymbol(input);
        LocalDate from = parseDate(fromInput);
        LocalDate to = parseDate(toInput);
        if (from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (to.equals(LocalDate.MAX)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "종료일이 올바르지 않습니다.");
        }

        // -12:00 is the last possible timezone to finish the requested date.
        // Filter each candle by its own market-local date after fetching.
        String before = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.ofHours(-12)).toString();
        List<DailyClose> prices = new ArrayList<>();
        Set<LocalDate> seenDates = new HashSet<>();
        Set<String> seenCursors = new HashSet<>();
        for (int page = 0; page < 1000; page++) {
            if (!seenCursors.add(before)) throw invalidResponse();
            JsonNode response = client.dailyCandles(symbol, before);
            JsonNode result = response == null ? null : response.get("result");
            JsonNode candles = result == null ? null : result.get("candles");
            if (candles == null || !candles.isArray()) throw invalidResponse();
            LocalDate oldest = null;
            for (JsonNode candle : candles) {
                if (candle == null || !candle.isObject()) throw invalidResponse();
                JsonNode timestamp = candle.get("timestamp");
                JsonNode close = candle.get("closePrice");
                JsonNode currency = candle.get("currency");
                if (timestamp == null || !timestamp.isTextual()
                        || close == null || !close.isTextual()
                        || currency == null || !currency.isTextual() || currency.asText().isBlank()) {
                    throw invalidResponse();
                }
                LocalDate date;
                try {
                    date = OffsetDateTime.parse(timestamp.asText()).toLocalDate();
                    new BigDecimal(close.asText());
                } catch (DateTimeParseException | NumberFormatException exception) {
                    throw invalidResponse();
                }
                if (oldest == null || date.isBefore(oldest)) oldest = date;
                if (!date.isBefore(from) && !date.isAfter(to) && seenDates.add(date)) {
                    prices.add(new DailyClose(date.toString(), close.asText(), currency.asText()));
                }
            }
            if (oldest != null && oldest.isBefore(from)) break;
            JsonNode nextBefore = result.get("nextBefore");
            if (nextBefore == null || nextBefore.isNull()) break;
            if (!nextBefore.isTextual() || nextBefore.asText().isBlank() || candles.isEmpty()) {
                throw invalidResponse();
            }
            try {
                OffsetDateTime.parse(nextBefore.asText());
            } catch (DateTimeParseException exception) {
                throw invalidResponse();
            }
            before = nextBefore.asText();
            if (page == 999) throw invalidResponse();
        }
        return new DailyCloseResponse(symbol, prices);
    }

    private String validateSymbol(String input) {
        String symbol = input == null ? "" : input.trim();
        if (symbol.isEmpty() || !SYMBOL.matcher(symbol).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "종목코드는 영문, 숫자, 점, 하이픈만 입력해 주세요.");
        }
        return symbol;
    }

    private LocalDate parseDate(String input) {
        try {
            if (input == null) throw new DateTimeParseException("missing", "", 0);
            return LocalDate.parse(input);
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "시작일과 종료일을 올바른 날짜로 입력해 주세요.");
        }
    }

    private StockPriceResponse mapPrice(JsonNode price) {
        JsonNode symbol = price.get("symbol");
        JsonNode lastPrice = price.get("lastPrice");
        JsonNode currency = price.get("currency");
        JsonNode timestamp = price.get("timestamp");
        if (symbol == null || !symbol.isTextual() || symbol.textValue().isBlank()
                || lastPrice == null || !lastPrice.isTextual() || lastPrice.textValue().isBlank()
                || currency == null || !currency.isTextual() || currency.textValue().isBlank()) {
            throw invalidResponse();
        }
        try {
            new BigDecimal(lastPrice.textValue());
        } catch (NumberFormatException exception) {
            throw invalidResponse();
        }
        String time = null;
        if (timestamp != null && !timestamp.isNull()) {
            if (!timestamp.isTextual()) throw invalidResponse();
            time = timestamp.textValue();
            try {
                OffsetDateTime.parse(time);
            } catch (DateTimeParseException exception) {
                throw invalidResponse();
            }
        }
        return new StockPriceResponse(symbol.textValue(), lastPrice.textValue(), currency.textValue(), time);
    }

    private ApiException invalidResponse() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "시세 응답 형식이 올바르지 않습니다.");
    }
}
