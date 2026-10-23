package com.example.backend.balance;

import static com.example.backend.balance.StockBalanceDtos.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StockBalanceRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public StockBalanceRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockAccounts(List<Long> ids) {
        for (Long id : ids.stream().distinct().sorted().toList()) {
            jdbc.queryForList("SELECT account_id FROM investment_account WHERE account_id = :id FOR UPDATE", Map.of("id", id));
        }
    }

    public boolean activeAccount(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM investment_account a JOIN brokerage_company b ON b.brokerage_id = a.brokerage_id WHERE a.account_id = :id AND a.use_yn AND b.use_yn)", Map.of("id", id), Boolean.class));
    }

    public boolean activeStock(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM stock_master WHERE stock_id = :id AND use_yn)", Map.of("id", id), Boolean.class));
    }

    public List<BalanceResponse> findBalances(Long brokerageId, Long accountId, Long stockId) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (brokerageId != null) { where.append(" AND a.brokerage_id = :brokerageId"); params.put("brokerageId", brokerageId); }
        if (accountId != null) { where.append(" AND b.account_id = :accountId"); params.put("accountId", accountId); }
        if (stockId != null) { where.append(" AND b.stock_id = :stockId"); params.put("stockId", stockId); }
        return jdbc.query("""
                SELECT b.balance_id, b.account_id, a.brokerage_id, c.brokerage_name, a.account_name,
                       b.stock_id, s.stock_code, s.stock_name, s.currency_code,
                       b.balance_quantity, b.current_price,
                       b.purchase_amount, b.purchase_price, b.evaluation_amount, b.evaluation_profit_loss, b.return_rate,
                       b.balance_updated_at
                  FROM stock_balance b
                  JOIN investment_account a ON a.account_id = b.account_id
                  JOIN brokerage_company c ON c.brokerage_id = a.brokerage_id
                  JOIN stock_master s ON s.stock_id = b.stock_id
                """ + where + " ORDER BY c.brokerage_name, a.account_name, s.stock_name, b.stock_id", params, this::mapBalance);
    }

    public Optional<BalanceResponse> findBalance(long accountId, long stockId) {
        return findBalances(null, accountId, stockId).stream().findFirst();
    }

    public Optional<BalanceKey> findBalanceKey(long balanceId) {
        return jdbc.query("SELECT account_id, stock_id FROM stock_balance WHERE balance_id = :id",
                Map.of("id", balanceId), (rs, row) -> new BalanceKey(rs.getLong(1), rs.getLong(2)))
                .stream().findFirst();
    }

    public int deleteBalance(long balanceId) {
        return jdbc.update("DELETE FROM stock_balance WHERE balance_id = :id", Map.of("id", balanceId));
    }

    public void removeApplications(long accountId, long stockId) {
        jdbc.update("DELETE FROM stock_balance_trade_application WHERE account_id = :accountId AND stock_id = :stockId",
                Map.of("accountId", accountId, "stockId", stockId));
    }

    public record BalanceKey(long accountId, long stockId) {}

    public void saveManual(BalanceRequest r) {
        jdbc.update("""
                INSERT INTO stock_balance (account_id, stock_id, balance_quantity,
                  current_price, purchase_amount, purchase_price, evaluation_amount,
                  evaluation_profit_loss, return_rate, balance_updated_at)
                VALUES (:accountId, :stockId, :quantity, :currentPrice,
                  :purchaseAmount, :purchasePrice, :evaluationAmount, :profitLoss, :returnRate, CURRENT_TIMESTAMP)
                ON CONFLICT (account_id, stock_id) DO UPDATE SET
                  balance_quantity = EXCLUDED.balance_quantity,
                  current_price = EXCLUDED.current_price,
                  purchase_amount = EXCLUDED.purchase_amount, purchase_price = EXCLUDED.purchase_price,
                  evaluation_amount = EXCLUDED.evaluation_amount,
                  evaluation_profit_loss = EXCLUDED.evaluation_profit_loss, return_rate = EXCLUDED.return_rate,
                  balance_updated_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                """, new MapSqlParameterSource().addValue("accountId", r.accountId()).addValue("stockId", r.stockId())
                .addValue("quantity", r.balanceQuantity()).addValue("currentPrice", r.currentPrice())
                .addValue("purchaseAmount", r.purchaseAmount()).addValue("purchasePrice", r.purchasePrice())
                .addValue("evaluationAmount", r.evaluationAmount())
                .addValue("profitLoss", r.evaluationProfitLoss()).addValue("returnRate", r.returnRate()));
    }

    public BigDecimal quantity(long accountId, long stockId) {
        List<BigDecimal> values = jdbc.query("SELECT balance_quantity FROM stock_balance WHERE account_id = :accountId AND stock_id = :stockId",
                Map.of("accountId", accountId, "stockId", stockId), (rs, row) -> rs.getBigDecimal(1));
        return values.isEmpty() ? BigDecimal.ZERO : values.get(0);
    }

    public void applyDelta(long accountId, long stockId, BigDecimal quantity) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("accountId", accountId)
                .addValue("stockId", stockId).addValue("quantity", quantity);
        int updated = jdbc.update("""
                UPDATE stock_balance SET
                  balance_quantity = balance_quantity + :quantity,
                  purchase_amount = CASE WHEN :quantity = 0 THEN purchase_amount ELSE NULL END,
                  purchase_price = CASE WHEN :quantity = 0 THEN purchase_price ELSE NULL END,
                  evaluation_amount = CASE WHEN :quantity = 0 THEN evaluation_amount ELSE NULL END,
                  evaluation_profit_loss = CASE WHEN :quantity = 0 THEN evaluation_profit_loss ELSE NULL END,
                  return_rate = CASE WHEN :quantity = 0 THEN return_rate ELSE NULL END,
                  balance_updated_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                 WHERE account_id = :accountId AND stock_id = :stockId
                """, params);
        if (updated == 0) jdbc.update("""
                INSERT INTO stock_balance (account_id, stock_id, balance_quantity, balance_updated_at)
                VALUES (:accountId, :stockId, :quantity, CURRENT_TIMESTAMP)
                """, params);
    }

    public Optional<AppliedTrade> applied(long tradeId) {
        return jdbc.query("SELECT trade_id, account_id, stock_id, trade_type, applied_quantity FROM stock_balance_trade_application WHERE trade_id = :id",
                Map.of("id", tradeId), (rs, row) -> new AppliedTrade(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getBigDecimal(5))).stream().findFirst();
    }

    public void saveApplication(long tradeId, long accountId, long stockId, String type, BigDecimal quantity) {
        jdbc.update("""
                INSERT INTO stock_balance_trade_application (trade_id, account_id, stock_id, trade_type, applied_quantity)
                VALUES (:tradeId, :accountId, :stockId, :type, :quantity)
                ON CONFLICT (trade_id) DO UPDATE SET account_id = EXCLUDED.account_id,
                  stock_id = EXCLUDED.stock_id, trade_type = EXCLUDED.trade_type,
                  applied_quantity = EXCLUDED.applied_quantity, updated_at = CURRENT_TIMESTAMP
                """, Map.of("tradeId", tradeId, "accountId", accountId, "stockId", stockId, "type", type, "quantity", quantity));
    }

    public void removeApplication(long tradeId) {
        jdbc.update("DELETE FROM stock_balance_trade_application WHERE trade_id = :id", Map.of("id", tradeId));
    }

    public void snapshot(long accountId) {
        Long id = jdbc.queryForObject("""
                INSERT INTO stock_balance_snapshot (account_id, snapshot_at)
                VALUES (:id, GREATEST(CURRENT_TIMESTAMP, COALESCE((SELECT MAX(snapshot_at) + INTERVAL '1 microsecond' FROM stock_balance_snapshot WHERE account_id = :id), CURRENT_TIMESTAMP)))
                RETURNING snapshot_id
                """, Map.of("id", accountId), Long.class);
        jdbc.update("""
                INSERT INTO stock_balance_snapshot_detail (snapshot_id, stock_id, balance_quantity,
                  current_price, purchase_amount, purchase_price, evaluation_amount, evaluation_profit_loss, return_rate)
                SELECT :id, stock_id, balance_quantity, current_price,
                  purchase_amount, purchase_price, evaluation_amount, evaluation_profit_loss, return_rate
                  FROM stock_balance WHERE account_id = :accountId
                """, Map.of("id", id, "accountId", accountId));
    }

    public List<SnapshotHeader> snapshots(long accountId) {
        return jdbc.query("SELECT snapshot_id, account_id, snapshot_at FROM stock_balance_snapshot WHERE account_id = :id ORDER BY snapshot_at DESC, snapshot_id DESC",
                Map.of("id", accountId), (rs, row) -> new SnapshotHeader(rs.getLong(1), rs.getLong(2), dateTime(rs, "snapshot_at")));
    }

    public Optional<SnapshotResponse> findSnapshot(long snapshotId) {
        List<SnapshotHeader> heads = jdbc.query("SELECT snapshot_id, account_id, snapshot_at FROM stock_balance_snapshot WHERE snapshot_id = :id",
                Map.of("id", snapshotId), (rs, row) -> new SnapshotHeader(rs.getLong(1), rs.getLong(2), dateTime(rs, "snapshot_at")));
        if (heads.isEmpty()) return Optional.empty();
        List<BalanceResponse> details = jdbc.query("""
                SELECT d.snapshot_detail_id AS balance_id, h.account_id, a.brokerage_id, c.brokerage_name, a.account_name,
                       d.stock_id, s.stock_code, s.stock_name, s.currency_code,
                       d.balance_quantity, d.current_price,
                       d.purchase_amount, d.purchase_price, d.evaluation_amount,
                       d.evaluation_profit_loss, d.return_rate,
                       h.snapshot_at AS balance_updated_at
                  FROM stock_balance_snapshot_detail d
                  JOIN stock_balance_snapshot h ON h.snapshot_id = d.snapshot_id
                  JOIN investment_account a ON a.account_id = h.account_id
                  JOIN brokerage_company c ON c.brokerage_id = a.brokerage_id
                  JOIN stock_master s ON s.stock_id = d.stock_id
                 WHERE d.snapshot_id = :id ORDER BY s.stock_name
                """, Map.of("id", snapshotId), this::mapBalance);
        return Optional.of(new SnapshotResponse(heads.get(0), details));
    }

    public boolean accountReferenced(long accountId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM stock_balance WHERE account_id = :id)
                    OR EXISTS (SELECT 1 FROM stock_balance_snapshot WHERE account_id = :id)
                    OR EXISTS (SELECT 1 FROM stock_balance_trade_application WHERE account_id = :id)
                """, Map.of("id", accountId), Boolean.class));
    }

    public boolean stockReferenced(long stockId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM stock_balance WHERE stock_id = :id)
                    OR EXISTS (SELECT 1 FROM stock_balance_snapshot_detail WHERE stock_id = :id)
                    OR EXISTS (SELECT 1 FROM stock_balance_trade_application WHERE stock_id = :id)
                """, Map.of("id", stockId), Boolean.class));
    }

    private BalanceResponse mapBalance(ResultSet rs, int row) throws SQLException {
        return new BalanceResponse(rs.getLong("balance_id"), rs.getLong("account_id"), rs.getLong("brokerage_id"),
                rs.getString("brokerage_name"), rs.getString("account_name"), rs.getLong("stock_id"),
                rs.getString("stock_code"), rs.getString("stock_name"), rs.getString("currency_code"),
                rs.getBigDecimal("balance_quantity"), rs.getBigDecimal("current_price"),
                rs.getBigDecimal("purchase_amount"), rs.getBigDecimal("purchase_price"),
                rs.getBigDecimal("evaluation_amount"),
                rs.getBigDecimal("evaluation_profit_loss"), rs.getBigDecimal("return_rate"),
                dateTime(rs, "balance_updated_at"));
    }

    private LocalDateTime dateTime(ResultSet rs, String name) throws SQLException {
        return rs.getTimestamp(name).toLocalDateTime();
    }
}
