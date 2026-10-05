package com.example.backend.master;

import static com.example.backend.master.InvestmentMasterDtos.*;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/master")
public class InvestmentMasterController {
    private final InvestmentMasterService service;

    public InvestmentMasterController(InvestmentMasterService service) {
        this.service = service;
    }

    @GetMapping("/brokerages")
    public PageResponse<BrokerageResponse> findBrokerages(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean useYn,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.findBrokerages(keyword, useYn, safePage(page), safeSize(size));
    }

    @GetMapping("/brokerages/{brokerageId}")
    public BrokerageResponse findBrokerage(@PathVariable long brokerageId) {
        return service.findBrokerage(brokerageId);
    }

    @PostMapping("/brokerages")
    public ResponseEntity<BrokerageResponse> createBrokerage(@RequestBody BrokerageRequest request) {
        BrokerageResponse created = service.createBrokerage(request);
        return ResponseEntity.created(URI.create("/api/master/brokerages/" + created.brokerageId())).body(created);
    }

    @PutMapping("/brokerages/{brokerageId}")
    public BrokerageResponse updateBrokerage(
            @PathVariable long brokerageId, @RequestBody BrokerageRequest request) {
        return service.updateBrokerage(brokerageId, request);
    }

    @PatchMapping("/brokerages/{brokerageId}/usage")
    public BrokerageResponse updateBrokerageUsage(
            @PathVariable long brokerageId, @RequestBody UsageRequest request) {
        return service.updateBrokerageUsage(brokerageId, request);
    }

    @GetMapping("/investment-accounts")
    public PageResponse<AccountResponse> findAccounts(
            @RequestParam(required = false) Long brokerageId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean useYn,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.findAccounts(brokerageId, keyword, useYn, safePage(page), safeSize(size));
    }

    @GetMapping("/investment-accounts/{accountId}")
    public AccountResponse findAccount(@PathVariable long accountId) {
        return service.findAccount(accountId);
    }

    @PostMapping("/investment-accounts")
    public ResponseEntity<AccountResponse> createAccount(@RequestBody AccountRequest request) {
        AccountResponse created = service.createAccount(request);
        return ResponseEntity.created(URI.create("/api/master/investment-accounts/" + created.accountId())).body(created);
    }

    @PutMapping("/investment-accounts/{accountId}")
    public AccountResponse updateAccount(
            @PathVariable long accountId, @RequestBody AccountRequest request) {
        return service.updateAccount(accountId, request);
    }

    @PatchMapping("/investment-accounts/{accountId}/usage")
    public AccountResponse updateAccountUsage(
            @PathVariable long accountId, @RequestBody UsageRequest request) {
        return service.updateAccountUsage(accountId, request);
    }

    private int safePage(int page) {
        return Math.max(page, 0);
    }

    private int safeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
