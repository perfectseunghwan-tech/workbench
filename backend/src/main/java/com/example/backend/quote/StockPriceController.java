package com.example.backend.quote;

import com.example.backend.quote.StockPriceDtos.StockPriceResponse;
import com.example.backend.quote.StockPriceDtos.DailyCloseResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock-prices")
public class StockPriceController {
    private final StockPriceService service;

    public StockPriceController(StockPriceService service) {
        this.service = service;
    }

    @GetMapping
    public StockPriceResponse find(@RequestParam(required = false) String symbol) {
        return service.find(symbol);
    }

    @GetMapping("/daily-closes")
    public DailyCloseResponse dailyCloses(@RequestParam(required = false) String symbol,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return service.dailyCloses(symbol, from, to);
    }
}
