package com.example.backend.trade;

import static com.example.backend.trade.StockTradeDtos.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockTradeService {
    private final StockTradeRepository repository;

    public StockTradeService(StockTradeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TradeResponse> findAll(TradeFilter filter) {
        if (filter.dateFrom() != null && filter.dateTo() != null && filter.dateFrom().isAfter(filter.dateTo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (filter.tradeType() != null && !filter.tradeType().isBlank()) {
            validateTradeType(filter.tradeType());
        }
        return repository.findAll(filter);
    }

    @Transactional(readOnly = true)
    public TradeResponse findById(long tradeId) {
        return repository.findById(tradeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "거래 내역을 찾을 수 없습니다."));
    }

    @Transactional
    public TradeResponse create(TradeRequest request) {
        TradeRequest normalized = validateAndNormalize(request);
        BigDecimal amount = calculateExecutionAmount(normalized.executionQuantity(), normalized.executionPrice());
        return findSaved(repository.insert(normalized, amount));
    }

    @Transactional
    public TradeResponse update(long tradeId, TradeRequest request) {
        findById(tradeId);
        TradeRequest normalized = validateAndNormalize(request);
        BigDecimal amount = calculateExecutionAmount(normalized.executionQuantity(), normalized.executionPrice());
        if (repository.update(tradeId, normalized, amount) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "거래 내역을 찾을 수 없습니다.");
        }
        return findSaved(tradeId);
    }

    @Transactional
    public void delete(long tradeId) {
        if (repository.delete(tradeId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "거래 내역을 찾을 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<BrokerageResponse> findBrokerages() {
        return repository.findBrokerages();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findAccounts(Long brokerageId) {
        return repository.findAccounts(brokerageId);
    }

    @Transactional(readOnly = true)
    public List<StockResponse> findStocks(String keyword, String marketCode) {
        return repository.findStocks(keyword, marketCode);
    }

    BigDecimal calculateExecutionAmount(BigDecimal quantity, BigDecimal price) {
        return quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    private TradeResponse findSaved(long tradeId) {
        return repository.findById(tradeId)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "저장된 거래를 조회하지 못했습니다."));
    }

    private TradeRequest validateAndNormalize(TradeRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "거래 정보가 필요합니다.");
        }
        if (request.accountId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "증권계좌를 선택해 주세요.");
        }
        if (request.stockId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "종목을 선택해 주세요.");
        }
        if (request.tradeDate() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "거래일을 입력해 주세요.");
        }
        String tradeType = request.tradeType() == null ? "" : request.tradeType().trim().toUpperCase();
        validateTradeType(tradeType);
        if (request.executionQuantity() == null || request.executionQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "체결수량은 0보다 커야 합니다.");
        }
        if (request.executionPrice() == null || request.executionPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "체결단가는 0 이상이어야 합니다.");
        }
        if (!repository.activeAccountExists(request.accountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "사용 가능한 증권계좌가 아닙니다.");
        }
        if (!repository.activeStockExists(request.stockId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "사용 가능한 종목이 아닙니다.");
        }

        BigDecimal purchasePrice = "BUY".equals(tradeType) ? null : request.purchasePrice();
        BigDecimal realizedProfitLoss = "BUY".equals(tradeType) ? null : request.realizedProfitLoss();
        if (purchasePrice != null && purchasePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "매입단가는 0 이상이어야 합니다.");
        }

        String memo = request.tradeMemo();
        if (memo != null) {
            memo = memo.trim();
            if (memo.isEmpty()) memo = null;
        }
        return new TradeRequest(
                request.accountId(), request.stockId(), request.tradeDate(), tradeType,
                request.executionQuantity(), request.executionPrice(), purchasePrice,
                realizedProfitLoss, memo);
    }

    private void validateTradeType(String tradeType) {
        if (!"BUY".equalsIgnoreCase(tradeType) && !"SELL".equalsIgnoreCase(tradeType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "거래구분은 BUY 또는 SELL이어야 합니다.");
        }
    }
}
