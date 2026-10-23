package com.example.backend.balance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class StockBalanceDtos {
    private StockBalanceDtos() {}

    public record BalanceRequest(Long accountId, Long stockId, BigDecimal balanceQuantity,
            BigDecimal currentPrice, BigDecimal purchaseAmount, BigDecimal purchasePrice, BigDecimal evaluationAmount,
            BigDecimal evaluationProfitLoss, BigDecimal returnRate) {}

    public record BalanceResponse(Long balanceId, Long accountId, Long brokerageId,
            String brokerageName, String accountName, Long stockId, String stockCode,
            String stockName, String currencyCode, BigDecimal balanceQuantity,
            BigDecimal currentPrice, BigDecimal purchaseAmount, BigDecimal purchasePrice, BigDecimal evaluationAmount,
            BigDecimal evaluationProfitLoss, BigDecimal returnRate, LocalDateTime balanceUpdatedAt) {}

    public record SnapshotHeader(Long snapshotId, Long accountId, LocalDateTime snapshotAt) {}
    public record SnapshotResponse(SnapshotHeader header, List<BalanceResponse> details) {}

    public record ApplicationRequest(List<Long> tradeIds) {}
    public record ApplicationGroup(Long accountId, Long brokerageId, String brokerageName,
            String accountName, Long stockId, String stockCode, String stockName,
            String currencyCode, BigDecimal buyQuantity, BigDecimal sellQuantity,
            BigDecimal currentQuantity, BigDecimal expectedQuantity) {}

    public record AppliedTrade(Long tradeId, Long accountId, Long stockId,
            String tradeType, BigDecimal appliedQuantity) {}
}
