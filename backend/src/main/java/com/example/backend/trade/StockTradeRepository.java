package com.example.backend.trade;

import static com.example.backend.trade.StockTradeDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StockTradeRepository {
    private static final String TRADE_FROM = """
            FROM stock_trade t
            JOIN investment_account a ON a.account_id = t.account_id
            JOIN brokerage_company b ON b.brokerage_id = a.brokerage_id
            JOIN stock_master s ON s.stock_id = t.stock_id
            """;

    private static final String TRADE_SELECT = """
            SELECT t.trade_id, t.account_id, a.brokerage_id, b.brokerage_name, a.account_name,
                   t.stock_id, s.stock_code, s.stock_name, s.market_code, s.currency_code,
                   t.trade_date, t.trade_type, t.execution_quantity, t.execution_price,
                   t.execution_amount, t.purchase_price, t.realized_profit_loss, t.trade_memo,
                   t.created_at, t.updated_at
            """ + TRADE_FROM;

    private final NamedParameterJdbcTemplate jdbc;

    public StockTradeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<TradeResponse> findAll(TradeFilter filter) {
        Map<String, Object> parameters = new HashMap<>();
        String where = buildWhere(filter, parameters);
        parameters.put("limit", filter.size());
        parameters.put("offset", filter.page() * filter.size());

        List<TradeResponse> content = jdbc.query(
                TRADE_SELECT + where + " ORDER BY t.trade_date DESC, t.trade_id DESC LIMIT :limit OFFSET :offset",
                parameters,
                tradeMapper());
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + TRADE_FROM + where, parameters, Long.class);
        long count = total == null ? 0 : total;
        int totalPages = count == 0 ? 0 : (int) Math.ceil((double) count / filter.size());
        return new PageResponse<>(content, count, totalPages, filter.page(), filter.size());
    }

    public Optional<TradeResponse> findById(long tradeId) {
        List<TradeResponse> result = jdbc.query(
                TRADE_SELECT + " WHERE t.trade_id = :tradeId",
                Map.of("tradeId", tradeId),
                tradeMapper());
        return result.stream().findFirst();
    }

    public long insert(TradeRequest request, java.math.BigDecimal executionAmount) {
        MapSqlParameterSource parameters = tradeParameters(request, executionAmount)
                .addValue("tradeId", null);
        Long id = jdbc.queryForObject("""
                INSERT INTO stock_trade (
                    account_id, stock_id, trade_date, trade_type,
                    execution_quantity, execution_price, execution_amount,
                    purchase_price, realized_profit_loss, trade_memo
                ) VALUES (
                    :accountId, :stockId, :tradeDate, :tradeType,
                    :executionQuantity, :executionPrice, :executionAmount,
                    :purchasePrice, :realizedProfitLoss, :tradeMemo
                )
                RETURNING trade_id
                """, parameters, Long.class);
        if (id == null) {
            throw new IllegalStateException("거래 ID를 생성하지 못했습니다.");
        }
        return id;
    }

    public int update(long tradeId, TradeRequest request, java.math.BigDecimal executionAmount) {
        MapSqlParameterSource parameters = tradeParameters(request, executionAmount)
                .addValue("tradeId", tradeId);
        return jdbc.update("""
                UPDATE stock_trade
                   SET account_id = :accountId,
                       stock_id = :stockId,
                       trade_date = :tradeDate,
                       trade_type = :tradeType,
                       execution_quantity = :executionQuantity,
                       execution_price = :executionPrice,
                       execution_amount = :executionAmount,
                       purchase_price = :purchasePrice,
                       realized_profit_loss = :realizedProfitLoss,
                       trade_memo = :tradeMemo,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE trade_id = :tradeId
                """, parameters);
    }

    public int delete(long tradeId) {
        return jdbc.update("DELETE FROM stock_trade WHERE trade_id = :tradeId", Map.of("tradeId", tradeId));
    }

    public boolean activeAccountExists(long accountId) {
        Boolean result = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM investment_account
                     WHERE account_id = :accountId AND use_yn = TRUE
                )
                """, Map.of("accountId", accountId), Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public boolean activeStockExists(long stockId) {
        Boolean result = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM stock_master
                     WHERE stock_id = :stockId AND use_yn = TRUE
                )
                """, Map.of("stockId", stockId), Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public List<BrokerageResponse> findBrokerages() {
        return jdbc.query("""
                SELECT brokerage_id, brokerage_name
                  FROM brokerage_company
                 WHERE use_yn = TRUE
                 ORDER BY brokerage_name
                """, Map.of(), (rs, rowNum) -> new BrokerageResponse(
                rs.getLong("brokerage_id"), rs.getString("brokerage_name")));
    }

    public List<AccountResponse> findAccounts(Long brokerageId) {
        String filter = brokerageId == null ? "" : " AND a.brokerage_id = :brokerageId";
        Map<String, Object> parameters = brokerageId == null ? Map.of() : Map.of("brokerageId", brokerageId);
        return jdbc.query("""
                SELECT a.account_id, a.brokerage_id, b.brokerage_name, a.account_name, a.account_number
                  FROM investment_account a
                  JOIN brokerage_company b ON b.brokerage_id = a.brokerage_id
                 WHERE a.use_yn = TRUE AND b.use_yn = TRUE
                """ + filter + " ORDER BY b.brokerage_name, a.account_name", parameters,
                (rs, rowNum) -> new AccountResponse(
                        rs.getLong("account_id"),
                        rs.getLong("brokerage_id"),
                        rs.getString("brokerage_name"),
                        rs.getString("account_name"),
                        rs.getString("account_number")));
    }

    public List<StockResponse> findStocks(String keyword, String marketCode) {
        StringBuilder where = new StringBuilder(" WHERE use_yn = TRUE");
        Map<String, Object> parameters = new HashMap<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (LOWER(stock_code) LIKE :keyword OR LOWER(stock_name) LIKE :keyword)");
            parameters.put("keyword", "%" + keyword.trim().toLowerCase() + "%");
        }
        if (marketCode != null && !marketCode.isBlank()) {
            where.append(" AND market_code = :marketCode");
            parameters.put("marketCode", marketCode.trim());
        }
        parameters.put("limit", 200);
        return jdbc.query("""
                SELECT stock_id, stock_code, stock_name, market_code, currency_code
                  FROM stock_master
                """ + where + " ORDER BY stock_name LIMIT :limit", parameters,
                (rs, rowNum) -> new StockResponse(
                        rs.getLong("stock_id"),
                        rs.getString("stock_code"),
                        rs.getString("stock_name"),
                        rs.getString("market_code"),
                        rs.getString("currency_code")));
    }

    private String buildWhere(TradeFilter filter, Map<String, Object> parameters) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (filter.dateFrom() != null) {
            where.append(" AND t.trade_date >= :dateFrom");
            parameters.put("dateFrom", filter.dateFrom());
        }
        if (filter.dateTo() != null) {
            where.append(" AND t.trade_date <= :dateTo");
            parameters.put("dateTo", filter.dateTo());
        }
        if (filter.brokerageId() != null) {
            where.append(" AND a.brokerage_id = :brokerageId");
            parameters.put("brokerageId", filter.brokerageId());
        }
        if (filter.accountId() != null) {
            where.append(" AND t.account_id = :accountId");
            parameters.put("accountId", filter.accountId());
        }
        if (filter.stockId() != null) {
            where.append(" AND t.stock_id = :stockId");
            parameters.put("stockId", filter.stockId());
        }
        if (filter.marketCode() != null && !filter.marketCode().isBlank()) {
            where.append(" AND s.market_code = :marketCode");
            parameters.put("marketCode", filter.marketCode().trim());
        }
        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            where.append(" AND (LOWER(s.stock_code) LIKE :keyword OR LOWER(s.stock_name) LIKE :keyword)");
            parameters.put("keyword", "%" + filter.keyword().trim().toLowerCase() + "%");
        }
        if (filter.tradeType() != null && !filter.tradeType().isBlank()) {
            where.append(" AND t.trade_type = :tradeType");
            parameters.put("tradeType", filter.tradeType().trim().toUpperCase());
        }
        return where.toString();
    }

    private MapSqlParameterSource tradeParameters(TradeRequest request, java.math.BigDecimal executionAmount) {
        return new MapSqlParameterSource()
                .addValue("accountId", request.accountId())
                .addValue("stockId", request.stockId())
                .addValue("tradeDate", request.tradeDate())
                .addValue("tradeType", request.tradeType())
                .addValue("executionQuantity", request.executionQuantity())
                .addValue("executionPrice", request.executionPrice())
                .addValue("executionAmount", executionAmount)
                .addValue("purchasePrice", request.purchasePrice())
                .addValue("realizedProfitLoss", request.realizedProfitLoss())
                .addValue("tradeMemo", request.tradeMemo());
    }

    private RowMapper<TradeResponse> tradeMapper() {
        return (ResultSet rs, int rowNum) -> new TradeResponse(
                rs.getLong("trade_id"),
                rs.getLong("account_id"),
                rs.getLong("brokerage_id"),
                rs.getString("brokerage_name"),
                rs.getString("account_name"),
                rs.getLong("stock_id"),
                rs.getString("stock_code"),
                rs.getString("stock_name"),
                rs.getString("market_code"),
                rs.getString("currency_code"),
                rs.getObject("trade_date", java.time.LocalDate.class),
                rs.getString("trade_type"),
                rs.getBigDecimal("execution_quantity"),
                rs.getBigDecimal("execution_price"),
                rs.getBigDecimal("execution_amount"),
                rs.getBigDecimal("purchase_price"),
                rs.getBigDecimal("realized_profit_loss"),
                rs.getString("trade_memo"),
                toLocalDateTime(rs, "created_at"),
                toLocalDateTime(rs, "updated_at"));
    }

    private java.time.LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
