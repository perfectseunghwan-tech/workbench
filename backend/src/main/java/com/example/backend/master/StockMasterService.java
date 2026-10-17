package com.example.backend.master;

import static com.example.backend.master.StockMasterDtos.*;

import com.example.backend.trade.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockMasterService {
    private final StockMasterRepository repository;
    public StockMasterService(StockMasterRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true) public PageResponse<StockResponse> findStocks(String keyword, String marketCode, Boolean useYn, int page, int size, String sortField, String sortDirection) { return repository.findStocks(keyword, marketCode, useYn, page, size, sortField, sortDirection); }
    @Transactional(readOnly = true) public StockResponse findStock(long id) { return repository.findById(id).orElseThrow(() -> notFound()); }
    @Transactional public StockResponse create(StockRequest request) { StockRequest normalized = normalize(request); unique(normalized, null); return saved(repository.insert(normalized)); }
    @Transactional public StockResponse update(long id, StockRequest request) { findStock(id); StockRequest normalized = normalize(request); unique(normalized, id); if (repository.update(id, normalized) == 0) throw notFound(); return saved(id); }
    @Transactional public StockResponse updateUsage(long id, UsageRequest request) { if (request == null || request.useYn() == null) throw bad("사용 여부를 입력해 주세요."); if (repository.updateUsage(id, request.useYn()) == 0) throw notFound(); return saved(id); }
    @Transactional public void delete(long id) { findStock(id); if (repository.hasTrades(id)) throw new ApiException(HttpStatus.CONFLICT, "거래 내역에서 사용 중인 종목은 삭제할 수 없습니다. 사용 중지로 변경해 주세요."); if (repository.delete(id) == 0) throw notFound(); }
    private StockRequest normalize(StockRequest r) {
        if (r == null) throw bad("종목 정보가 필요합니다.");
        String code = required(r.stockCode(), "종목코드를 입력해 주세요.", 30, "종목코드는 30자 이하로 입력해 주세요.");
        String name = required(r.stockName(), "종목명을 입력해 주세요.", 200, "종목명은 200자 이하로 입력해 주세요.");
        String market = clean(r.marketCode()); if (market != null && market.length() > 30) throw bad("시장코드는 30자 이하로 입력해 주세요.");
        String currency = clean(r.currencyCode()); if (currency == null) currency = "KRW"; if (currency.length() > 10) throw bad("통화코드는 10자 이하로 입력해 주세요.");
        return new StockRequest(code, name, market, currency, r.useYn() == null || r.useYn());
    }
    private String required(String value, String missing, int max, String tooLong) { String cleaned = clean(value); if (cleaned == null) throw bad(missing); if (cleaned.length() > max) throw bad(tooLong); return cleaned; }
    private String clean(String value) { if (value == null) return null; String trimmed = value.trim(); return trimmed.isEmpty() ? null : trimmed; }
    private void unique(StockRequest r, Long excluded) { if (repository.stockCodeAndMarketExists(r.stockCode(), r.marketCode(), excluded)) throw new ApiException(HttpStatus.CONFLICT, "이미 등록된 종목코드와 시장코드 조합입니다."); }
    private StockResponse saved(long id) { return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "저장된 종목을 조회하지 못했습니다.")); }
    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."); }
}
