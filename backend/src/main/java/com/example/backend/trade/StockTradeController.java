package com.example.backend.trade;

import static com.example.backend.trade.StockTradeDtos.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StockTradeController {
    private final StockTradeService service;

    public StockTradeController(StockTradeService service) {
        this.service = service;
    }

    @GetMapping("/stock-trades")
    public PageResponse<TradeResponse> findAll(
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) Long brokerageId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long stockId,
            @RequestParam(required = false) String marketCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tradeType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return service.findAll(new TradeFilter(
                dateFrom, dateTo, brokerageId, accountId, stockId,
                marketCode, keyword, tradeType, safePage, safeSize));
    }

    @GetMapping("/stock-trades/{tradeId}")
    public TradeResponse findById(@PathVariable long tradeId) {
        return service.findById(tradeId);
    }

    @PostMapping("/stock-trades")
    public ResponseEntity<TradeResponse> create(@RequestBody TradeRequest request) {
        TradeResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/stock-trades/" + created.tradeId())).body(created);
    }

    @PutMapping("/stock-trades/{tradeId}")
    public TradeResponse update(@PathVariable long tradeId, @RequestBody TradeRequest request) {
        return service.update(tradeId, request);
    }

    @DeleteMapping("/stock-trades/{tradeId}")
    public ResponseEntity<Void> delete(@PathVariable long tradeId) {
        service.delete(tradeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/brokerages")
    public List<BrokerageResponse> findBrokerages() {
        return service.findBrokerages();
    }

    @GetMapping("/investment-accounts")
    public List<AccountResponse> findAccounts(@RequestParam(required = false) Long brokerageId) {
        return service.findAccounts(brokerageId);
    }

    @GetMapping("/stocks")
    public List<StockResponse> findStocks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String marketCode) {
        return service.findStocks(keyword, marketCode);
    }
}
