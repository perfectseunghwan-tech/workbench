package com.example.backend.master;

import static com.example.backend.master.StockMasterDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StockMasterRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public StockMasterRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PageResponse<StockResponse> findStocks(String keyword, String marketCode, Boolean useYn, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        String where = where(keyword, marketCode, useYn, params);
        params.put("limit", size); params.put("offset", page * size);
        List<StockResponse> content = jdbc.query(select() + where
                + " ORDER BY stock_name, stock_code, stock_id LIMIT :limit OFFSET :offset", params, mapper());
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM stock_master" + where, params, Long.class);
        long count = total == null ? 0 : total;
        return new PageResponse<>(content, count, count == 0 ? 0 : (int) Math.ceil((double) count / size), page, size);
    }

    public Optional<StockResponse> findById(long stockId) {
        return jdbc.query(select() + " WHERE stock_id = :stockId", Map.of("stockId", stockId), mapper()).stream().findFirst();
    }

    public boolean stockCodeAndMarketExists(String stockCode, String marketCode, Long excludedId) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("stockCode", stockCode).addValue("marketCode", marketCode);
        String exclude = "";
        if (excludedId != null) { exclude = " AND stock_id <> :excludedId"; p.addValue("excludedId", excludedId); }
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM stock_master WHERE stock_code = :stockCode "
                + "AND market_code IS NOT DISTINCT FROM :marketCode" + exclude + ")", p, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public long insert(StockRequest request) {
        Long id = jdbc.queryForObject("""
                INSERT INTO stock_master (stock_code, stock_name, market_code, currency_code, use_yn)
                VALUES (:stockCode, :stockName, :marketCode, :currencyCode, :useYn) RETURNING stock_id
                """, parameters(request), Long.class);
        if (id == null) throw new IllegalStateException("종목 ID를 생성하지 못했습니다.");
        return id;
    }

    public int update(long stockId, StockRequest request) {
        return jdbc.update("""
                UPDATE stock_master SET stock_code = :stockCode, stock_name = :stockName,
                    market_code = :marketCode, currency_code = :currencyCode, use_yn = :useYn,
                    updated_at = CURRENT_TIMESTAMP WHERE stock_id = :stockId
                """, parameters(request).addValue("stockId", stockId));
    }
    public int updateUsage(long stockId, boolean useYn) {
        return jdbc.update("UPDATE stock_master SET use_yn = :useYn, updated_at = CURRENT_TIMESTAMP WHERE stock_id = :stockId",
                Map.of("stockId", stockId, "useYn", useYn));
    }
    public boolean hasTrades(long stockId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM stock_trade WHERE stock_id = :stockId)", Map.of("stockId", stockId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }
    public int delete(long stockId) { return jdbc.update("DELETE FROM stock_master WHERE stock_id = :stockId", Map.of("stockId", stockId)); }

    private String select() { return "SELECT stock_id, stock_code, stock_name, market_code, currency_code, use_yn, created_at, updated_at FROM stock_master"; }
    private String where(String keyword, String marketCode, Boolean useYn, Map<String, Object> p) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (keyword != null && !keyword.isBlank()) { where.append(" AND (LOWER(stock_code) LIKE :keyword OR LOWER(stock_name) LIKE :keyword)"); p.put("keyword", "%" + keyword.trim().toLowerCase() + "%"); }
        if (marketCode != null && !marketCode.isBlank()) { where.append(" AND market_code = :marketCode"); p.put("marketCode", marketCode.trim()); }
        if (useYn != null) { where.append(" AND use_yn = :useYn"); p.put("useYn", useYn); }
        return where.toString();
    }
    private MapSqlParameterSource parameters(StockRequest r) { return new MapSqlParameterSource().addValue("stockCode", r.stockCode()).addValue("stockName", r.stockName()).addValue("marketCode", r.marketCode()).addValue("currencyCode", r.currencyCode()).addValue("useYn", r.useYn()); }
    private RowMapper<StockResponse> mapper() { return (ResultSet rs, int row) -> new StockResponse(rs.getLong("stock_id"), rs.getString("stock_code"), rs.getString("stock_name"), rs.getString("market_code"), rs.getString("currency_code"), rs.getBoolean("use_yn"), timestamp(rs, "created_at"), timestamp(rs, "updated_at")); }
    private LocalDateTime timestamp(ResultSet rs, String name) throws SQLException { var value = rs.getTimestamp(name); return value == null ? null : value.toLocalDateTime(); }
}
