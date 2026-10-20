package com.example.backend.balance;

import static com.example.backend.balance.StockBalanceDtos.*;

import java.util.List;
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

    public StockBalanceController(StockBalanceService service) { this.service = service; }

    @GetMapping("/stock-balances")
    public List<BalanceResponse> find(@RequestParam(required = false) Long brokerageId,
            @RequestParam(required = false) Long accountId, @RequestParam(required = false) Long stockId) {
        return service.findBalances(brokerageId, accountId, stockId);
    }

    @PutMapping("/stock-balances")
    public BalanceResponse save(@RequestBody BalanceRequest request) { return service.save(request); }

    @GetMapping("/stock-balances/snapshots")
    public List<SnapshotHeader> snapshots(@RequestParam long accountId) { return service.snapshots(accountId); }

    @GetMapping("/stock-balances/snapshots/{snapshotId}")
    public SnapshotResponse snapshot(@PathVariable long snapshotId) { return service.snapshot(snapshotId); }

    @PostMapping("/stock-balance-applications/preview")
    public List<ApplicationGroup> preview(@RequestBody ApplicationRequest request) { return service.preview(request); }

    @PostMapping("/stock-balance-applications")
    public List<ApplicationGroup> apply(@RequestBody ApplicationRequest request) { return service.apply(request); }
}
