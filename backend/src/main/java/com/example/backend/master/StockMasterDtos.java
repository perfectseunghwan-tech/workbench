package com.example.backend.master;

import java.time.LocalDateTime;
import java.util.List;

public final class StockMasterDtos {
    private StockMasterDtos() {}

    public record StockRequest(String stockCode, String stockName, String marketCode, String currencyCode, Boolean useYn) {}
    public record StockResponse(Long stockId, String stockCode, String stockName, String marketCode,
                                String currencyCode, Boolean useYn, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record UsageRequest(Boolean useYn) {}
    public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int page, int size) {}
}
