package com.example.backend.journal;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class InvestmentJournalDtos {
    private InvestmentJournalDtos() {}

    public record JournalSearchFilter(LocalDate dateFrom, LocalDate dateTo, String keyword,
            int page, int size, String sortField, String sortDirection) {}
    public record NewFileManifest(String uploadKey, String fileType, String originalFileName, int sortOrder) {}
    public record JournalSaveRequest(LocalDate journalDate, String journalTitle, String contentFormat,
            JsonNode journalContent, List<Long> retainedFileIds, List<NewFileManifest> newFiles,
            List<Long> relatedStrategyIds) {
        public JournalSaveRequest(LocalDate journalDate, String journalTitle, String contentFormat,
                JsonNode journalContent, List<Long> retainedFileIds, List<NewFileManifest> newFiles) {
            this(journalDate, journalTitle, contentFormat, journalContent, retainedFileIds, newFiles, List.of());
        }
    }
    public record JournalStrategyResponse(Long strategyId, String title, String status) {}
    public record JournalSummaryResponse(Long journalId, LocalDate journalDate, String journalTitle,
            int fileCount, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record JournalFileResponse(Long fileId, String fileType, String originalFileName,
            String mimeType, Long fileSize, int sortOrder, LocalDateTime createdAt, String contentUrl) {}
    public record JournalDetailResponse(Long journalId, LocalDate journalDate, String journalTitle,
            int fileCount, LocalDateTime createdAt, LocalDateTime updatedAt, String contentFormat,
            JsonNode journalContent, List<JournalFileResponse> files, List<JournalStrategyResponse> relatedStrategies) {
        public JournalDetailResponse(Long journalId, LocalDate journalDate, String journalTitle,
                int fileCount, LocalDateTime createdAt, LocalDateTime updatedAt, String contentFormat,
                JsonNode journalContent, List<JournalFileResponse> files) {
            this(journalId, journalDate, journalTitle, fileCount, createdAt, updatedAt,
                    contentFormat, journalContent, files, List.of());
        }
    }
    public record JournalPageResponse(List<JournalSummaryResponse> content, long totalElements,
            int totalPages, int page, int size) {}

    record JournalRow(Long journalId, LocalDate journalDate, String journalTitle, String contentFormat,
            JsonNode journalContent, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    record FileRow(Long fileId, Long journalId, String fileType, String originalFileName,
            String storedFileName, String filePath, String mimeType, Long fileSize,
            int sortOrder, LocalDateTime createdAt) {}
    record StoredFile(String originalFileName, String storedFileName, String filePath, String stagingPath,
            String mimeType, long fileSize, String fileType, int sortOrder) {}
}
