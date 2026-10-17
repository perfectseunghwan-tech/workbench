package com.example.backend.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.example.backend.journal.InvestmentJournalDtos.NewFileManifest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class InvestmentStrategyDtos {
    private InvestmentStrategyDtos() {}

    public record StrategyFilter(String keyword, String strategyScope, String strategyType,
            String investmentHorizon, String status, String sortField, String sortDirection, int page, int size) {}
    public record StrategySaveRequest(String strategyScope, String strategyType, String investmentHorizon,
            String title, JsonNode content, String status, List<Long> stockIds,
            List<Long> retainedFileIds, List<NewFileManifest> newFiles) {}
    public record PostSaveRequest(String postType, String title, JsonNode content, LocalDate postDate,
            List<Long> retainedFileIds, List<NewFileManifest> newFiles) {}
    public record ContentFile(Long fileId, String fileType, String originalFileName,
            @JsonIgnore String filePath, String mimeType, Long fileSize, int sortOrder, LocalDateTime createdAt,
            String contentUrl) {}
    public record StockItem(Long stockId, String stockCode, String stockName, String marketCode, String currencyCode) {}
    public record StrategyLink(Long strategyId, String title, String status) {}
    public record StrategySummary(Long strategyId, String strategyScope, String strategyType,
            String investmentHorizon, String title, String status, Integer versionNo, List<StockItem> stocks,
            LocalDateTime latestActivityAt, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record StrategyPage(List<StrategySummary> content, long totalElements, int totalPages, int page, int size) {}
    public record StrategyPost(Long postId, String postType, String title, JsonNode content,
            LocalDate postDate, LocalDateTime createdAt, LocalDateTime updatedAt, List<ContentFile> files) {}
    public record StrategyHistory(Long historyId, Integer versionNo, String title, JsonNode content,
            LocalDateTime archivedAt) {}
    public record LinkedJournal(Long journalId, LocalDate journalDate, String journalTitle, LocalDateTime updatedAt) {}
    public record RelatedTrade(Long tradeId, LocalDate tradeDate, String tradeType, Long stockId,
            String stockCode, String stockName, String brokerageName, String accountName,
            BigDecimal executionQuantity, BigDecimal executionPrice, BigDecimal executionAmount,
            BigDecimal realizedProfitLoss, String tradeMemo) {}
    public record StrategyDetail(Long strategyId, String strategyScope, String strategyType,
            String investmentHorizon, String title, JsonNode content, String status, Integer versionNo, List<StockItem> stocks,
            List<StrategyPost> posts, List<StrategyHistory> histories, List<LinkedJournal> journals,
            List<RelatedTrade> trades, List<ContentFile> files, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    record StrategyRow(Long strategyId, String strategyScope, String strategyType, String investmentHorizon,
            String title, JsonNode content, String status, Integer versionNo, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
