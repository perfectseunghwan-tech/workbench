package com.example.backend.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.master.StockMasterDtos.StockRequest;
import com.example.backend.master.StockMasterDtos.StockResponse;
import com.example.backend.trade.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class StockMasterServiceTest {
    @Mock
    private StockMasterRepository repository;

    @InjectMocks
    private StockMasterService service;

    @Test
    void createRejectsAnExistingStockCodeAndMarketCombination() {
        StockRequest request = new StockRequest("005930", "삼성전자", "KOSPI", "KRW", true);
        when(repository.stockCodeAndMarketExists("005930", "KOSPI", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiException.getMessage())
                            .isEqualTo("이미 등록된 종목코드와 시장코드 조합입니다.");
                });

        verify(repository, never()).insert(request);
    }

    @Test
    void createAllowsTheSameStockCodeInADifferentMarket() {
        StockRequest request = new StockRequest("005930", "삼성전자", "KOSDAQ", "KRW", true);
        StockResponse saved = new StockResponse(
                20L, "005930", "삼성전자", "KOSDAQ", "KRW", true, null, null);
        when(repository.stockCodeAndMarketExists("005930", "KOSDAQ", null)).thenReturn(false);
        when(repository.insert(request)).thenReturn(20L);
        when(repository.findById(20L)).thenReturn(Optional.of(saved));

        assertThat(service.create(request)).isEqualTo(saved);
    }

    @Test
    void updateRejectsAnotherStockWithTheSameCodeAndMarket() {
        long stockId = 10L;
        StockRequest request = new StockRequest("005930", "삼성전자", "KOSPI", "KRW", true);
        when(repository.findById(stockId)).thenReturn(Optional.of(new StockResponse(
                stockId, "000660", "SK하이닉스", "KOSPI", "KRW", true, null, null)));
        when(repository.stockCodeAndMarketExists("005930", "KOSPI", stockId)).thenReturn(true);

        assertThatThrownBy(() -> service.update(stockId, request))
                .isInstanceOf(ApiException.class)
                .hasMessage("이미 등록된 종목코드와 시장코드 조합입니다.");

        verify(repository, never()).update(stockId, request);
    }
}
