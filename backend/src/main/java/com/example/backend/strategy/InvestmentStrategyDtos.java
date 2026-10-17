package com.example.backend.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class InvestmentStrategyDtos {
    private InvestmentStrategyDtos() {}

    public record StrategyFilter(String keyword, String strategyScope, String categoryCode,
            String status, String sortField, String sortDirection, int page, int size) {}
    public record StrategySaveRequest(String strategyScope, List<String> categoryCodes, String title,
            JsonNode content, String status, List<Long> stockIds) {}
    public record PostSaveRequest(String postType, String title, JsonNode content, LocalDate postDate) {}
    public record StockItem(Long stockId, String stockCode, String stockName, String marketCode, String currencyCode) {}
    public record StrategyLink(Long strategyId, String title, String status) {}
    public record StrategySummary(Long strategyId, String strategyScope, List<String> categoryCodes,
            String title, String status, Integer versionNo, List<StockItem> stocks,
            LocalDateTime latestActivityAt, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record StrategyPage(List<StrategySummary> content, long totalElements, int totalPages, int page, int size) {}
    public record StrategyPost(Long postId, String postType, String title, JsonNode content,
            LocalDate postDate, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record StrategyHistory(Long historyId, Integer versionNo, String title, JsonNode content,
            LocalDateTime archivedAt) {}
    public record LinkedJournal(Long journalId, LocalDate journalDate, String journalTitle, LocalDateTime updatedAt) {}
    public record RelatedTrade(Long tradeId, LocalDate tradeDate, String tradeType, Long stockId,
            String stockCode, String stockName, String brokerageName, String accountName,
            BigDecimal executionQuantity, BigDecimal executionPrice, BigDecimal executionAmount,
            BigDecimal realizedProfitLoss, String tradeMemo) {}
    public record StrategyDetail(Long strategyId, String strategyScope, List<String> categoryCodes,
            String title, JsonNode content, String status, Integer versionNo, List<StockItem> stocks,
            List<StrategyPost> posts, List<StrategyHistory> histories, List<LinkedJournal> journals,
            List<RelatedTrade> trades, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    record StrategyRow(Long strategyId, String strategyScope, List<String> categoryCodes, String title,
            JsonNode content, String status, Integer versionNo, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
