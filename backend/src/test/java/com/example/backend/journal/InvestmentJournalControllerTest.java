package com.example.backend.journal;

import static com.example.backend.journal.InvestmentJournalDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InvestmentJournalControllerTest {
    private final InvestmentJournalService service = mock(InvestmentJournalService.class);
    private final InvestmentJournalController controller = new InvestmentJournalController(service, mock(JournalFileStorageService.class));

    @Test
    void clampsPagingParameters() {
        when(service.findAll(any())).thenAnswer(invocation -> {
            JournalSearchFilter filter = invocation.getArgument(0);
            assertThat(filter.page()).isZero();
            assertThat(filter.size()).isEqualTo(100);
            return new JournalPageResponse(List.of(), 0, 0, filter.page(), filter.size());
        });
        controller.list(null, null, null, -1, 500);
        verify(service).findAll(any());
    }

    @Test
    void createReturnsLocationAndCreatedStatus() {
        var detail = new JournalDetailResponse(7L, LocalDate.now(), null, 0,
                LocalDateTime.now(), LocalDateTime.now(), "JSON", null, List.of());
        when(service.create(any(), any())).thenReturn(detail);
        var response = controller.create(mock(JournalSaveRequest.class), List.of());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/investment-journals/7");
    }
}
