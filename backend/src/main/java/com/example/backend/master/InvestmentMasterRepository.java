package com.example.backend.master;

import static com.example.backend.master.InvestmentMasterDtos.*;

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
public class InvestmentMasterRepository {
    private static final String BROKERAGE_SELECT = """
            SELECT b.brokerage_id, b.brokerage_name, b.use_yn, b.created_at, b.updated_at,
                   COUNT(a.account_id) AS account_count
              FROM brokerage_company b
              LEFT JOIN investment_account a ON a.brokerage_id = b.brokerage_id
            """;
    private static final String ACCOUNT_SELECT = """
            SELECT a.account_id, a.brokerage_id, b.brokerage_name, a.account_name,
                   a.account_number, m.memo_content AS latest_memo_content,
                   m.created_at AS latest_memo_created_at, a.use_yn, a.created_at, a.updated_at
              FROM investment_account a
              JOIN brokerage_company b ON b.brokerage_id = a.brokerage_id
              LEFT JOIN LATERAL (
                  SELECT memo_content, created_at
                    FROM investment_account_memo
                   WHERE account_id = a.account_id
                   ORDER BY created_at DESC, memo_id DESC
                   LIMIT 1
              ) m ON TRUE
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public InvestmentMasterRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<BrokerageResponse> findBrokerages(
            String keyword, Boolean useYn, int page, int size) {
        Map<String, Object> parameters = new HashMap<>();
        String where = brokerageWhere(keyword, useYn, parameters);
        parameters.put("limit", size);
        parameters.put("offset", page * size);

        List<BrokerageResponse> content = jdbc.query(
                BROKERAGE_SELECT + where
                        + " GROUP BY b.brokerage_id ORDER BY b.brokerage_name LIMIT :limit OFFSET :offset",
                parameters,
                brokerageMapper());
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM brokerage_company b" + where,
                parameters,
                Long.class);
        return page(content, total, page, size);
    }

    public Optional<BrokerageResponse> findBrokerageById(long brokerageId) {
        return jdbc.query(
                BROKERAGE_SELECT
                        + " WHERE b.brokerage_id = :brokerageId GROUP BY b.brokerage_id",
                Map.of("brokerageId", brokerageId),
                brokerageMapper()).stream().findFirst();
    }

    public boolean brokerageNameExists(String brokerageName, Long excludedId) {
        String exclusion = excludedId == null ? "" : " AND brokerage_id <> :excludedId";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("brokerageName", brokerageName);
        if (excludedId != null) parameters.put("excludedId", excludedId);
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM brokerage_company
                     WHERE LOWER(brokerage_name) = LOWER(:brokerageName)
                """ + exclusion + ")", parameters, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public long insertBrokerage(BrokerageRequest request) {
        Long id = jdbc.queryForObject("""
                INSERT INTO brokerage_company (brokerage_name, use_yn)
                VALUES (:brokerageName, :useYn)
                RETURNING brokerage_id
                """, new MapSqlParameterSource()
                .addValue("brokerageName", request.brokerageName())
                .addValue("useYn", request.useYn()), Long.class);
        if (id == null) throw new IllegalStateException("증권사 ID를 생성하지 못했습니다.");
        return id;
    }

    public int updateBrokerage(long brokerageId, BrokerageRequest request) {
        return jdbc.update("""
                UPDATE brokerage_company
                   SET brokerage_name = :brokerageName,
                       use_yn = :useYn,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE brokerage_id = :brokerageId
                """, new MapSqlParameterSource()
                .addValue("brokerageId", brokerageId)
                .addValue("brokerageName", request.brokerageName())
                .addValue("useYn", request.useYn()));
    }

    public int updateBrokerageUsage(long brokerageId, boolean useYn) {
        return jdbc.update("""
                UPDATE brokerage_company
                   SET use_yn = :useYn, updated_at = CURRENT_TIMESTAMP
                 WHERE brokerage_id = :brokerageId
                """, Map.of("brokerageId", brokerageId, "useYn", useYn));
    }

    public boolean activeBrokerageExists(long brokerageId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM brokerage_company
                     WHERE brokerage_id = :brokerageId AND use_yn = TRUE
                )
                """, Map.of("brokerageId", brokerageId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public PageResponse<AccountResponse> findAccounts(
            Long brokerageId, String keyword, Boolean useYn, int page, int size) {
        Map<String, Object> parameters = new HashMap<>();
        String where = accountWhere(brokerageId, keyword, useYn, parameters);
        parameters.put("limit", size);
        parameters.put("offset", page * size);
        List<AccountResponse> content = jdbc.query(
                ACCOUNT_SELECT + where
                        + " ORDER BY b.brokerage_name, a.account_name LIMIT :limit OFFSET :offset",
                parameters,
                accountMapper());
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM investment_account a JOIN brokerage_company b"
                        + " ON b.brokerage_id = a.brokerage_id" + where,
                parameters,
                Long.class);
        return page(content, total, page, size);
    }

    public Optional<AccountResponse> findAccountById(long accountId) {
        return jdbc.query(
                ACCOUNT_SELECT + " WHERE a.account_id = :accountId",
                Map.of("accountId", accountId),
                accountMapper()).stream().findFirst();
    }

    public long insertAccount(AccountRequest request) {
        Long id = jdbc.queryForObject("""
                INSERT INTO investment_account (
                    brokerage_id, account_name, account_number, use_yn
                ) VALUES (
                    :brokerageId, :accountName, :accountNumber, :useYn
                )
                RETURNING account_id
                """, accountParameters(request).addValue("accountId", null), Long.class);
        if (id == null) throw new IllegalStateException("계좌 ID를 생성하지 못했습니다.");
        return id;
    }

    public int updateAccount(long accountId, AccountRequest request) {
        return jdbc.update("""
                UPDATE investment_account
                   SET brokerage_id = :brokerageId,
                       account_name = :accountName,
                       account_number = :accountNumber,
                       use_yn = :useYn,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE account_id = :accountId
                """, accountParameters(request).addValue("accountId", accountId));
    }

    public int updateAccountUsage(long accountId, boolean useYn) {
        return jdbc.update("""
                UPDATE investment_account
                   SET use_yn = :useYn, updated_at = CURRENT_TIMESTAMP
                 WHERE account_id = :accountId
                """, Map.of("accountId", accountId, "useYn", useYn));
    }

    public int deleteAccountTrades(long accountId) {
        return jdbc.update("DELETE FROM stock_trade WHERE account_id = :accountId", Map.of("accountId", accountId));
    }

    public int deleteAccount(long accountId) {
        return jdbc.update("DELETE FROM investment_account WHERE account_id = :accountId", Map.of("accountId", accountId));
    }

    public List<AccountMemoResponse> findAccountMemos(long accountId) {
        return jdbc.query("""
                SELECT memo_id, account_id, memo_content, created_at
                  FROM investment_account_memo
                 WHERE account_id = :accountId
                 ORDER BY created_at DESC, memo_id DESC
                """, Map.of("accountId", accountId), accountMemoMapper());
    }

    public AccountMemoResponse insertAccountMemo(long accountId, String memoContent) {
        return jdbc.queryForObject("""
                INSERT INTO investment_account_memo (account_id, memo_content)
                VALUES (:accountId, :memoContent)
                RETURNING memo_id, account_id, memo_content, created_at
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("memoContent", memoContent), accountMemoMapper());
    }

    public int updateAccountMemo(long accountId, long memoId, String memoContent) {
        return jdbc.update("""
                UPDATE investment_account_memo
                   SET memo_content = :memoContent
                 WHERE account_id = :accountId AND memo_id = :memoId
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("memoId", memoId)
                .addValue("memoContent", memoContent));
    }

    public Optional<AccountMemoResponse> findAccountMemo(long accountId, long memoId) {
        return jdbc.query("""
                SELECT memo_id, account_id, memo_content, created_at
                  FROM investment_account_memo
                 WHERE account_id = :accountId AND memo_id = :memoId
                """, Map.of("accountId", accountId, "memoId", memoId), accountMemoMapper()).stream().findFirst();
    }

    public int deleteAccountMemo(long accountId, long memoId) {
        return jdbc.update("""
                DELETE FROM investment_account_memo
                 WHERE account_id = :accountId AND memo_id = :memoId
                """, Map.of("accountId", accountId, "memoId", memoId));
    }

    public int deleteAccountMemos(long accountId) {
        return jdbc.update("DELETE FROM investment_account_memo WHERE account_id = :accountId", Map.of("accountId", accountId));
    }

    private String brokerageWhere(String keyword, Boolean useYn, Map<String, Object> parameters) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND LOWER(b.brokerage_name) LIKE :keyword");
            parameters.put("keyword", "%" + keyword.trim().toLowerCase() + "%");
        }
        if (useYn != null) {
            where.append(" AND b.use_yn = :useYn");
            parameters.put("useYn", useYn);
        }
        return where.toString();
    }

    private String accountWhere(
            Long brokerageId, String keyword, Boolean useYn, Map<String, Object> parameters) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (brokerageId != null) {
            where.append(" AND a.brokerage_id = :brokerageId");
            parameters.put("brokerageId", brokerageId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (LOWER(a.account_name) LIKE :keyword OR LOWER(COALESCE(a.account_number, '')) LIKE :keyword)");
            parameters.put("keyword", "%" + keyword.trim().toLowerCase() + "%");
        }
        if (useYn != null) {
            where.append(" AND a.use_yn = :useYn");
            parameters.put("useYn", useYn);
        }
        return where.toString();
    }

    private MapSqlParameterSource accountParameters(AccountRequest request) {
        return new MapSqlParameterSource()
                .addValue("brokerageId", request.brokerageId())
                .addValue("accountName", request.accountName())
                .addValue("accountNumber", request.accountNumber())
                .addValue("useYn", request.useYn());
    }

    private RowMapper<BrokerageResponse> brokerageMapper() {
        return (rs, rowNum) -> new BrokerageResponse(
                rs.getLong("brokerage_id"),
                rs.getString("brokerage_name"),
                rs.getBoolean("use_yn"),
                rs.getLong("account_count"),
                timestamp(rs, "created_at"),
                timestamp(rs, "updated_at"));
    }

    private RowMapper<AccountResponse> accountMapper() {
        return (rs, rowNum) -> {
            String accountNumber = rs.getString("account_number");
            return new AccountResponse(
                    rs.getLong("account_id"),
                    rs.getLong("brokerage_id"),
                    rs.getString("brokerage_name"),
                    rs.getString("account_name"),
                    accountNumber,
                    rs.getString("latest_memo_content"),
                    timestamp(rs, "latest_memo_created_at"),
                    rs.getBoolean("use_yn"),
                    timestamp(rs, "created_at"),
                    timestamp(rs, "updated_at"));
        };
    }

    private RowMapper<AccountMemoResponse> accountMemoMapper() {
        return (rs, rowNum) -> new AccountMemoResponse(
                rs.getLong("memo_id"), rs.getLong("account_id"),
                rs.getString("memo_content"), timestamp(rs, "created_at"));
    }

    private LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private <T> PageResponse<T> page(List<T> content, Long total, int page, int size) {
        long count = total == null ? 0 : total;
        int pages = count == 0 ? 0 : (int) Math.ceil((double) count / size);
        return new PageResponse<>(content, count, pages, page, size);
    }
}
