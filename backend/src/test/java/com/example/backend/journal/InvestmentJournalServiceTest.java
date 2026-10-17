package com.example.backend.journal;

import static com.example.backend.journal.InvestmentJournalDtos.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.backend.trade.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvestmentJournalServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final InvestmentJournalService service = new InvestmentJournalService(
            mock(InvestmentJournalRepository.class), mock(JournalFileStorageService.class),
            mapper, 30, 104857600, 2097152);

    @Test
    void rejectsReversedSearchDates() {
        assertThatThrownBy(() -> service.findAll(new JournalSearchFilter(
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1), null,
                0, 20, "journalDate", "desc")))
                .isInstanceOf(ApiException.class).hasMessageContaining("시작일");
    }

    @Test
    void rejectsMissingDateAndInvalidDocument() {
        var doc = mapper.createObjectNode().put("type", "doc");
        assertThatThrownBy(() -> service.create(new JournalSaveRequest(null, null, "JSON", doc, List.of(), List.of()), List.of()))
                .isInstanceOf(ApiException.class).hasMessageContaining("일지일자");
        var paragraph = mapper.createObjectNode().put("type", "paragraph");
        assertThatThrownBy(() -> service.create(new JournalSaveRequest(LocalDate.now(), null, "JSON", paragraph, List.of(), List.of()), List.of()))
                .isInstanceOf(ApiException.class).hasMessageContaining("Tiptap doc");
    }

    @Test
    void rejectsUnsupportedContentFormatAndLongTitle() {
        var doc = mapper.createObjectNode().put("type", "doc");
        assertThatThrownBy(() -> service.create(new JournalSaveRequest(LocalDate.now(), null, "HTML", doc, List.of(), List.of()), List.of()))
                .isInstanceOf(ApiException.class).hasMessageContaining("JSON");
        assertThatThrownBy(() -> service.create(new JournalSaveRequest(LocalDate.now(), "x".repeat(201), "JSON", doc, List.of(), List.of()), List.of()))
                .isInstanceOf(ApiException.class).hasMessageContaining("200자");
    }
}
