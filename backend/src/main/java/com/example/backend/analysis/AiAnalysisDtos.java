package com.example.backend.analysis;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

public final class AiAnalysisDtos {
    private AiAnalysisDtos() {}

    public record Target(String targetTypeCode, String targetCode, String targetName, boolean isPrimary) {}
    public record Relation(String relationTypeCode, long relatedId, String relationNote, String relatedTitle) {}
    public record FileInfo(long fileId, String fileType, String originalFileName, @JsonIgnore String filePath,
            String mimeType, Long fileSize, int sortOrder, String contentUrl) {}
    public record SaveRequest(String title, String analysisTypeCode, String analysisPurposeCode,
            String summary, String content, String contentFormat, String conclusion, String keyRisks,
            String sourceCode, String sourceUrl,
            LocalDateTime analyzedAt, LocalDateTime dataAsOf, String opinionCode,
            String reviewStatusCode, String userReview, List<Target> targets,
            List<Relation> relations, List<String> tags, List<Long> retainedFileIds,
            List<com.example.backend.journal.InvestmentJournalDtos.NewFileManifest> newFiles) {}
    public record Summary(long analysisId, String title, String analysisTypeCode,
            String analysisPurposeCode, String sourceCode, String opinionCode,
            String reviewStatusCode, LocalDateTime analyzedAt, List<Target> targets,
            List<String> tags) {}
    public record Detail(long analysisId, String title, String analysisTypeCode,
            String analysisPurposeCode, String summary, String content, String contentFormat,
            String conclusion, String keyRisks, String sourceCode, String sourceUrl,
            LocalDateTime analyzedAt, LocalDateTime dataAsOf,
            String opinionCode, String reviewStatusCode, String userReview, LocalDateTime reviewedAt,
            List<Target> targets, List<Relation> relations, List<String> tags, List<FileInfo> files) {}
    public record Page(List<Summary> content, long totalElements, int totalPages, int page, int size) {}
    public record RelatedOption(long id, String title) {}
}
