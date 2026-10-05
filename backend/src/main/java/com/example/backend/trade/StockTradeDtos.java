package com.example.backend.trade;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class StockTradeDtos {
    private StockTradeDtos() {
    }

    public record TradeRequest(
            Long accountId,
            Long stockId,
            LocalDate tradeDate,
            String tradeType,
            BigDecimal executionQuantity,
            BigDecimal executionPrice,
            BigDecimal purchasePrice,
            BigDecimal realizedProfitLoss,
            String tradeMemo) {
    }

    public record TradeResponse(
            Long tradeId,
            Long accountId,
            Long brokerageId,
            String brokerageName,
            String accountName,
            Long stockId,
            String stockCode,
            String stockName,
            String marketCode,
            String currencyCode,
            LocalDate tradeDate,
            String tradeType,
            BigDecimal executionQuantity,
            BigDecimal executionPrice,
            BigDecimal executionAmount,
            BigDecimal purchasePrice,
            BigDecimal realizedProfitLoss,
            String tradeMemo,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record TradeFilter(
            LocalDate dateFrom,
            LocalDate dateTo,
            Long brokerageId,
            Long accountId,
            Long stockId,
            String marketCode,
            String keyword,
            String tradeType,
            int page,
            int size) {
    }

    public record PageResponse<T>(
            List<T> content,
            long totalElements,
            int totalPages,
            int page,
            int size) {
    }

    public record BrokerageResponse(Long brokerageId, String brokerageName) {
    }

    public record AccountResponse(
            Long accountId,
            Long brokerageId,
            String brokerageName,
            String accountName,
            String accountNumber) {
    }

    public record StockResponse(
            Long stockId,
            String stockCode,
            String stockName,
            String marketCode,
            String currencyCode) {
    }
}
