package com.example.backend.analysis;

import static com.example.backend.analysis.AiAnalysisDtos.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import com.example.backend.journal.InvestmentJournalDtos.NewFileManifest;

@SpringBootTest
@Transactional
class AiAnalysisServiceTest {
    @Autowired AiAnalysisService service;

    @Test
    void savesSearchesAndSanitizesAnalysis() {
        LocalDateTime analyzed = LocalDateTime.of(2026, 10, 23, 12, 0);
        SaveRequest request = new SaveRequest("분석 테스트", "STOCK", "GENERAL", "요약",
                "<h2>본문</h2><script>alert(1)</script>", "HTML", "판단", "위험",
                "CHATGPT_APP", null, analyzed, null, "HOLD",
                "UNREVIEWED", null, List.of(new Target("STOCK", "000001", "대상", true)),
                List.of(), List.of("#검토", "검토"), List.of(), List.of());

        Detail saved = service.create(request, List.of());

        assertEquals("분석 테스트", service.find(saved.analysisId()).title());
        assertEquals(List.of("검토"), saved.tags());
        assertEquals(1, saved.targets().size());
        assertFalse(saved.content().contains("<script"));
        assertTrue(service.search(null, null, "분석 테스트", "STOCK", null, null,
                "대상", "검토", "UNREVIEWED", 0, 20).content().stream()
                .anyMatch(row -> row.analysisId() == saved.analysisId()));
    }

    @Test
    void linksUploadedImageToBody() {
        SaveRequest request = new SaveRequest("이미지 테스트", "STOCK", null, null,
                "<p>본문</p><img data-upload-key='image-1' src='blob:temporary'>", "HTML",
                null, null, "CHATGPT_APP", null, LocalDateTime.now(), null,
                null, "UNREVIEWED", null, List.of(), List.of(), List.of(), List.of(),
                List.of(new NewFileManifest("image-1", "IMAGE", "sample.png", 0)));
        MockMultipartFile image = new MockMultipartFile("files", "sample.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0, 0, 0, 0});

        Detail saved = service.create(request, List.of(image));

        assertEquals(1, saved.files().size());
        assertTrue(saved.content().contains(saved.files().get(0).contentUrl()));
        assertFalse(saved.content().contains("blob:temporary"));
    }

    @Test
    void removesIncomingAnalysisRelationOnDelete() {
        SaveRequest base = new SaveRequest("첫 분석", "MARKET", null, null, "<p>본문</p>",
                "HTML", null, null, "CLAUDE_APP", null, LocalDateTime.now(),
                null, null, "UNREVIEWED", null, List.of(), List.of(), List.of(), List.of(), List.of());
        long firstId = service.create(base, List.of()).analysisId();
        SaveRequest linked = new SaveRequest("둘째 분석", "MARKET", null, null, "<p>본문</p>",
                "HTML", null, null, "CLAUDE_APP", null, LocalDateTime.now(),
                null, null, "UNREVIEWED", null, List.of(),
                List.of(new Relation("ANALYSIS", firstId, null, null)), List.of(), List.of(), List.of());
        long secondId = service.create(linked, List.of()).analysisId();
        assertEquals(1, service.find(secondId).relations().size());

        service.delete(firstId);

        assertTrue(service.find(secondId).relations().isEmpty());
    }
}
