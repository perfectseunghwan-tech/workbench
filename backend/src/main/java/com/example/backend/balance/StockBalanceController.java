package com.example.backend.balance;

import static com.example.backend.balance.StockBalanceDtos.*;
import java.math.BigDecimal;

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
public class StockBalanceController {
    private final StockBalanceService service;
    private final StockBalancePriceRefreshService priceRefresh;

    public StockBalanceController(StockBalanceService service, StockBalancePriceRefreshService priceRefresh) {
        this.service = service;
        this.priceRefresh = priceRefresh;
    }

    @GetMapping("/stock-balances")
    public List<BalanceResponse> find(@RequestParam(required = false) Long brokerageId,
            @RequestParam(required = false) Long accountId, @RequestParam(required = false) Long stockId) {
        return service.findBalances(brokerageId, accountId, stockId);
    }

    @PutMapping("/stock-balances")
    public BalanceResponse save(@RequestBody BalanceRequest request) { return service.save(request); }

    @DeleteMapping("/stock-balances/{balanceId}")
    public ResponseEntity<Void> delete(@PathVariable long balanceId) {
        service.delete(balanceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/stock-balances/prices/refresh")
    public PriceRefreshResponse refreshPrices(@RequestBody PriceRefreshRequest request) {
        return priceRefresh.refresh(request);
    }

    @GetMapping("/stock-balances/exchange-rate")
    public BigDecimal exchangeRate(@RequestParam long accountId) { return priceRefresh.savedExchangeRate(accountId); }

    @GetMapping("/stock-balances/snapshots")
    public List<SnapshotHeader> snapshots(@RequestParam long accountId) { return service.snapshots(accountId); }

    @GetMapping("/stock-balances/snapshots/{snapshotId}")
    public SnapshotResponse snapshot(@PathVariable long snapshotId) { return service.snapshot(snapshotId); }

    @PostMapping("/stock-balance-applications/preview")
    public List<ApplicationGroup> preview(@RequestBody ApplicationRequest request) { return service.preview(request); }

    @PostMapping("/stock-balance-applications")
    public List<ApplicationGroup> apply(@RequestBody ApplicationRequest request) { return service.apply(request); }
}
