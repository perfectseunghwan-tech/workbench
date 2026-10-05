package com.example.backend.master;

import java.time.LocalDateTime;
import java.util.List;

public final class InvestmentMasterDtos {
    private InvestmentMasterDtos() {
    }

    public record BrokerageRequest(String brokerageName, Boolean useYn) {
    }

    public record BrokerageResponse(
            Long brokerageId,
            String brokerageName,
            Boolean useYn,
            long accountCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record AccountRequest(
            Long brokerageId,
            String accountName,
            String accountNumber,
            String accountMemo,
            Boolean useYn) {
    }

    public record AccountResponse(
            Long accountId,
            Long brokerageId,
            String brokerageName,
            String accountName,
            String accountNumber,
            String accountMemo,
            Boolean useYn,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record UsageRequest(Boolean useYn) {
    }

    public record PageResponse<T>(
            List<T> content,
            long totalElements,
            int totalPages,
            int page,
            int size) {
    }
}
