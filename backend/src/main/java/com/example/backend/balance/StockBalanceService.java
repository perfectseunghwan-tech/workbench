package com.example.backend.balance;

import static com.example.backend.balance.StockBalanceDtos.*;

import com.example.backend.trade.ApiException;
import com.example.backend.trade.StockTradeDtos.TradeRequest;
import com.example.backend.trade.StockTradeDtos.TradeResponse;
import com.example.backend.trade.StockTradeRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockBalanceService {
    private final StockBalanceRepository balances;
    private final StockTradeRepository trades;

    public StockBalanceService(StockBalanceRepository balances, StockTradeRepository trades) {
        this.balances = balances;
        this.trades = trades;
    }

    @Transactional(readOnly = true)
    public List<BalanceResponse> findBalances(Long brokerageId, Long accountId, Long stockId) {
        return balances.findBalances(brokerageId, accountId, stockId);
    }

    @Transactional
    public BalanceResponse save(BalanceRequest r) {
        if (r == null || r.accountId() == null || r.stockId() == null) throw bad("계좌와 종목을 선택해 주세요.");
        validateNumber(r.balanceQuantity(), 20, 6, true, "잔고수량");
        validateNumber(r.availableQuantity(), 20, 6, false, "매도가능수량");
        validateNumber(r.currentPrice(), 20, 6, false, "현재가");
        validateNumber(r.purchasePrice(), 20, 6, false, "평균 매입가");
        validateNumber(r.purchaseAmount(), 24, 2, false, "매입금액");
        validateNumber(r.evaluationAmount(), 24, 2, false, "평가금액");
        validateNumber(r.evaluationProfitLoss(), 24, 2, false, "평가손익", true);
        validateNumber(r.returnRate(), 12, 6, false, "수익률", true);
        if (r.availableQuantity() != null && r.availableQuantity().compareTo(r.balanceQuantity()) > 0)
            throw bad("매도가능수량은 잔고수량을 초과할 수 없습니다.");
        balances.lockAccounts(List.of(r.accountId()));
        if (!balances.activeAccount(r.accountId()) || !balances.activeStock(r.stockId()))
            throw bad("사용 가능한 계좌와 종목을 선택해 주세요.");
        balances.saveManual(r);
        balances.snapshot(r.accountId());
        return balances.findBalance(r.accountId(), r.stockId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<SnapshotHeader> snapshots(long accountId) { return balances.snapshots(accountId); }

    @Transactional(readOnly = true)
    public SnapshotResponse snapshot(long snapshotId) {
        return balances.findSnapshot(snapshotId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "스냅샷을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<ApplicationGroup> preview(ApplicationRequest request) {
        return groups(fetchSelected(request, true));
    }

    @Transactional
    public List<ApplicationGroup> apply(ApplicationRequest request) {
        List<TradeResponse> selected = fetchSelected(request, false);
        balances.lockAccounts(selected.stream().map(TradeResponse::accountId).toList());
        selected = fetchSelected(request, true);
        List<ApplicationGroup> groups = groups(selected);
        Map<Key, BigDecimal> prices = prices(request, groups);
        for (ApplicationGroup group : groups) {
            if (!balances.activeAccount(group.accountId()) || !balances.activeStock(group.stockId()))
                throw bad("사용 가능한 계좌와 종목의 거래만 반영할 수 있습니다.");
            if (group.expectedQuantity().signum() < 0) throw conflict("잔고수량이 음수가 됩니다.");
            validateNumber(group.expectedQuantity(), 20, 6, true, "잔고수량");
        }
        for (ApplicationGroup group : groups) {
            BigDecimal delta = group.buyQuantity().subtract(group.sellQuantity());
            balances.applyDelta(group.accountId(), group.stockId(), delta,
                    prices.get(new Key(group.accountId(), group.stockId())));
        }
        for (TradeResponse trade : selected) {
            balances.saveApplication(trade.tradeId(), trade.accountId(), trade.stockId(),
                    trade.tradeType(), trade.executionQuantity());
        }
        groups.stream().map(ApplicationGroup::accountId).distinct().sorted().forEach(balances::snapshot);
        return groups;
    }

    @Transactional
    public void reviseApplied(TradeResponse old, TradeRequest changed) {
        AppliedTrade applied = balances.applied(old.tradeId()).orElse(null);
        if (applied == null) return;
        List<Long> accounts = new ArrayList<>(List.of(applied.accountId()));
        if (changed != null) accounts.add(changed.accountId());
        balances.lockAccounts(accounts);
        Map<Key, BigDecimal> deltas = new LinkedHashMap<>();
        deltas.merge(new Key(applied.accountId(), applied.stockId()),
                signed(applied.tradeType(), applied.appliedQuantity()).negate(), BigDecimal::add);
        if (changed != null) deltas.merge(new Key(changed.accountId(), changed.stockId()),
                signed(changed.tradeType(), changed.executionQuantity()), BigDecimal::add);
        for (Map.Entry<Key, BigDecimal> item : deltas.entrySet()) {
            BigDecimal result = balances.quantity(item.getKey().accountId(), item.getKey().stockId()).add(item.getValue());
            if (result.signum() < 0) throw conflict("거래 변경으로 잔고수량이 음수가 됩니다. 잔고를 먼저 보정해 주세요.");
            validateNumber(result, 20, 6, true, "잔고수량");
        }
        for (Map.Entry<Key, BigDecimal> item : deltas.entrySet()) {
            if (item.getValue().signum() != 0)
                balances.applyDelta(item.getKey().accountId(), item.getKey().stockId(), item.getValue(), null);
        }
        if (changed == null) balances.removeApplication(old.tradeId());
        else balances.saveApplication(old.tradeId(), changed.accountId(), changed.stockId(),
                changed.tradeType(), changed.executionQuantity());
        accounts.stream().distinct().sorted().forEach(balances::snapshot);
    }

    private List<TradeResponse> fetchSelected(ApplicationRequest request, boolean checkApplied) {
        if (request == null || request.tradeIds() == null || request.tradeIds().isEmpty()
                || request.tradeIds().size() > 100 || request.tradeIds().stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(request.tradeIds()).size() != request.tradeIds().size())
            throw bad("중복되지 않은 거래를 1~100건 선택해 주세요.");
        List<TradeResponse> selected = new ArrayList<>();
        for (Long id : request.tradeIds()) {
            TradeResponse trade = trades.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "선택한 거래를 찾을 수 없습니다."));
            if (checkApplied && balances.applied(id).isPresent()) throw conflict("이미 잔고에 반영된 거래가 있습니다.");
            selected.add(trade);
        }
        return selected;
    }

    private List<ApplicationGroup> groups(List<TradeResponse> selected) {
        Map<Key, List<TradeResponse>> byKey = new LinkedHashMap<>();
        for (TradeResponse trade : selected)
            byKey.computeIfAbsent(new Key(trade.accountId(), trade.stockId()), ignored -> new ArrayList<>()).add(trade);
        List<ApplicationGroup> result = new ArrayList<>();
        for (Map.Entry<Key, List<TradeResponse>> entry : byKey.entrySet()) {
            TradeResponse first = entry.getValue().get(0);
            BigDecimal buy = BigDecimal.ZERO;
            BigDecimal sell = BigDecimal.ZERO;
            for (TradeResponse trade : entry.getValue()) {
                if ("BUY".equals(trade.tradeType())) buy = buy.add(trade.executionQuantity());
                else sell = sell.add(trade.executionQuantity());
            }
            BigDecimal current = balances.quantity(first.accountId(), first.stockId());
            result.add(new ApplicationGroup(first.accountId(), first.brokerageId(), first.brokerageName(),
                    first.accountName(), first.stockId(), first.stockCode(), first.stockName(), first.currencyCode(),
                    buy, sell, current, current.add(buy).subtract(sell)));
        }
        return result;
    }

    private Map<Key, BigDecimal> prices(ApplicationRequest request, List<ApplicationGroup> groups) {
        Set<Key> valid = new HashSet<>();
        for (ApplicationGroup group : groups) valid.add(new Key(group.accountId(), group.stockId()));
        Map<Key, BigDecimal> result = new HashMap<>();
        if (request.purchasePrices() == null) return result;
        for (PurchasePriceInput input : request.purchasePrices()) {
            if (input == null || input.accountId() == null || input.stockId() == null) throw bad("평균 매입가의 계좌와 종목이 필요합니다.");
            Key key = new Key(input.accountId(), input.stockId());
            if (!valid.contains(key) || result.containsKey(key)) throw bad("평균 매입가는 선택한 계좌·종목별로 한 번만 입력해 주세요.");
            validateNumber(input.purchasePrice(), 20, 6, false, "평균 매입가");
            result.put(key, input.purchasePrice());
        }
        return result;
    }

    private BigDecimal signed(String type, BigDecimal quantity) {
        return "BUY".equals(type) ? quantity : quantity.negate();
    }

    private void validateNumber(BigDecimal value, int precision, int scale, boolean required, String name) {
        validateNumber(value, precision, scale, required, name, false);
    }

    private void validateNumber(BigDecimal value, int precision, int scale, boolean required, String name, boolean signed) {
        if (value == null) { if (required) throw bad(name + "을(를) 입력해 주세요."); return; }
        if ((!signed && value.signum() < 0) || value.scale() > scale || value.precision() - value.scale() > precision - scale)
            throw bad(name + "의 범위 또는 소수 자릿수가 올바르지 않습니다.");
    }

    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    private ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
    private record Key(long accountId, long stockId) {}
}
