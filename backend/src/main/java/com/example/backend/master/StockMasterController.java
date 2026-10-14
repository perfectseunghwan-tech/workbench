package com.example.backend.master;

import static com.example.backend.master.StockMasterDtos.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/master/stocks")
public class StockMasterController {
    private final StockMasterService service;
    public StockMasterController(StockMasterService service) { this.service = service; }
    @GetMapping public PageResponse<StockResponse> find(@RequestParam(required = false) String keyword, @RequestParam(required = false) String marketCode, @RequestParam(required = false) Boolean useYn, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.findStocks(keyword, marketCode, useYn, Math.max(0, page), Math.min(100, Math.max(1, size))); }
    @GetMapping("/{stockId}") public StockResponse findOne(@PathVariable long stockId) { return service.findStock(stockId); }
    @PostMapping public ResponseEntity<StockResponse> create(@RequestBody StockRequest request) { StockResponse created = service.create(request); return ResponseEntity.created(URI.create("/api/master/stocks/" + created.stockId())).body(created); }
    @PutMapping("/{stockId}") public StockResponse update(@PathVariable long stockId, @RequestBody StockRequest request) { return service.update(stockId, request); }
    @PatchMapping("/{stockId}/usage") public StockResponse usage(@PathVariable long stockId, @RequestBody UsageRequest request) { return service.updateUsage(stockId, request); }
    @DeleteMapping("/{stockId}") public ResponseEntity<Void> delete(@PathVariable long stockId) { service.delete(stockId); return ResponseEntity.noContent().build(); }
}
