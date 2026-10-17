package com.example.backend.journal;

import static com.example.backend.journal.InvestmentJournalDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvestmentJournalRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public InvestmentJournalRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public JournalPageResponse findAll(JournalSearchFilter filter) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (filter.dateFrom() != null) { where.append(" AND j.journal_date >= :dateFrom"); params.put("dateFrom", filter.dateFrom()); }
        if (filter.dateTo() != null) { where.append(" AND j.journal_date <= :dateTo"); params.put("dateTo", filter.dateTo()); }
        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            where.append(" AND LOWER(COALESCE(j.journal_title, '')) LIKE :keyword");
            params.put("keyword", "%" + filter.keyword().trim().toLowerCase(Locale.ROOT) + "%");
        }
        params.put("limit", filter.size()); params.put("offset", filter.page() * filter.size());
        String from = " FROM investment_journal j";
        List<JournalSummaryResponse> content = jdbc.query("""
                SELECT j.journal_id, j.journal_date, j.journal_title, j.created_at, j.updated_at,
                       (SELECT COUNT(*) FROM investment_content_file f WHERE f.journal_id=j.journal_id) file_count
                """ + from + where + " ORDER BY " + orderBy(filter.sortField(), filter.sortDirection())
                        + " LIMIT :limit OFFSET :offset",
                params, (rs, n) -> new JournalSummaryResponse(rs.getLong("journal_id"),
                        rs.getObject("journal_date", java.time.LocalDate.class), rs.getString("journal_title"),
                        rs.getInt("file_count"), time(rs, "created_at"), time(rs, "updated_at")));
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, params, Long.class);
        long count = total == null ? 0 : total;
        return new JournalPageResponse(content, count, count == 0 ? 0 : (int)Math.ceil((double)count/filter.size()), filter.page(), filter.size());
    }

    private String orderBy(String sortField, String sortDirection) {
        String column = switch (sortField) {
            case "journalTitle" -> "j.journal_title";
            case "fileCount" -> "file_count";
            case "createdAt" -> "j.created_at";
            case "updatedAt" -> "j.updated_at";
            default -> "j.journal_date";
        };
        String direction = "asc".equalsIgnoreCase(sortDirection) ? "ASC" : "DESC";
        return column + " " + direction + " NULLS LAST, j.journal_id DESC";
    }

    public Optional<JournalRow> findById(long id) {
        return jdbc.query("SELECT journal_id,journal_date,journal_title,content_format,journal_content,created_at,updated_at FROM investment_journal WHERE journal_id=:id",
                Map.of("id", id), (rs, n) -> journal(rs)).stream().findFirst();
    }

    public List<FileRow> findFiles(long journalId) {
        return jdbc.query("SELECT file_id,journal_id,file_type,original_file_name,stored_file_name,file_path,mime_type,file_size,sort_order,created_at FROM investment_content_file WHERE journal_id=:id ORDER BY sort_order,file_id",
                Map.of("id", journalId), (rs, n) -> file(rs));
    }

    public Optional<FileRow> findFile(long journalId, long fileId) {
        return jdbc.query("SELECT file_id,journal_id,file_type,original_file_name,stored_file_name,file_path,mime_type,file_size,sort_order,created_at FROM investment_content_file WHERE journal_id=:journalId AND file_id=:fileId",
                Map.of("journalId", journalId, "fileId", fileId), (rs, n) -> file(rs)).stream().findFirst();
    }

    public long insert(JournalSaveRequest request, JsonNode content) {
        Long id = jdbc.queryForObject("""
                INSERT INTO investment_journal(journal_date,journal_title,content_format,journal_content)
                VALUES(:date,:title,'JSON',CAST(:content AS jsonb)) RETURNING journal_id
                """, journalParams(request, content), Long.class);
        if (id == null) throw new IllegalStateException("일지 ID를 생성하지 못했습니다.");
        return id;
    }

    public int update(long id, JournalSaveRequest request, JsonNode content) {
        MapSqlParameterSource p = journalParams(request, content).addValue("id", id);
        return jdbc.update("""
                UPDATE investment_journal SET journal_date=:date,journal_title=:title,content_format='JSON',
                journal_content=CAST(:content AS jsonb),updated_at=CURRENT_TIMESTAMP WHERE journal_id=:id
                """, p);
    }

    public long insertFile(long journalId, StoredFile f) {
        Long id = jdbc.queryForObject("""
                INSERT INTO investment_content_file(journal_id,file_type,original_file_name,stored_file_name,file_path,mime_type,file_size,sort_order)
                VALUES(:journalId,:fileType,:original,:stored,:path,:mime,:size,:sortOrder) RETURNING file_id
                """, new MapSqlParameterSource().addValue("journalId", journalId).addValue("fileType", f.fileType())
                .addValue("original", f.originalFileName()).addValue("stored", f.storedFileName())
                .addValue("path", f.filePath()).addValue("mime", f.mimeType()).addValue("size", f.fileSize())
                .addValue("sortOrder", f.sortOrder()), Long.class);
        if (id == null) throw new IllegalStateException("파일 ID를 생성하지 못했습니다.");
        return id;
    }

    public void deleteFiles(long journalId, Collection<Long> retained) {
        if (retained.isEmpty()) jdbc.update("DELETE FROM investment_content_file WHERE journal_id=:id", Map.of("id", journalId));
        else jdbc.update("DELETE FROM investment_content_file WHERE journal_id=:id AND file_id NOT IN (:ids)", Map.of("id", journalId, "ids", retained));
    }
    public void updateFileOrder(long journalId, List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("UPDATE investment_content_file SET sort_order=:sortOrder WHERE journal_id=:journalId AND file_id=:fileId",
                    Map.of("sortOrder", i, "journalId", journalId, "fileId", ids.get(i)));
        }
    }
    public int delete(long id) { return jdbc.update("DELETE FROM investment_journal WHERE journal_id=:id", Map.of("id", id)); }

    public boolean strategiesExist(List<Long> ids) {
        if (ids.isEmpty()) return true;
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM investment_strategy WHERE use_yn=TRUE AND strategy_id IN (:ids)", Map.of("ids", ids), Long.class);
        return count != null && count == new HashSet<>(ids).size();
    }

    public void replaceStrategies(long journalId, List<Long> ids) {
        jdbc.update("DELETE FROM investment_strategy_journal WHERE journal_id=:id", Map.of("id", journalId));
        for (Long strategyId : ids) jdbc.update("INSERT INTO investment_strategy_journal(strategy_id,journal_id) VALUES(:strategyId,:journalId)", Map.of("strategyId", strategyId, "journalId", journalId));
    }

    public List<JournalStrategyResponse> findStrategies(long journalId) {
        return jdbc.query("""
                SELECT s.strategy_id,s.title,s.status FROM investment_strategy_journal x
                JOIN investment_strategy s ON s.strategy_id=x.strategy_id
                WHERE x.journal_id=:id AND s.use_yn=TRUE ORDER BY s.title
                """, Map.of("id", journalId), (rs,n)->new JournalStrategyResponse(rs.getLong(1),rs.getString(2),rs.getString(3)));
    }

    private MapSqlParameterSource journalParams(JournalSaveRequest r, JsonNode content) {
        try { return new MapSqlParameterSource().addValue("date", r.journalDate()).addValue("title", r.journalTitle()).addValue("content", mapper.writeValueAsString(content)); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("본문을 직렬화할 수 없습니다.", e); }
    }
    private JournalRow journal(ResultSet rs) throws SQLException {
        try { return new JournalRow(rs.getLong("journal_id"), rs.getObject("journal_date", java.time.LocalDate.class), rs.getString("journal_title"), rs.getString("content_format"), mapper.readTree(rs.getString("journal_content")), time(rs,"created_at"), time(rs,"updated_at")); }
        catch (JsonProcessingException e) { throw new SQLException("잘못된 일지 JSON", e); }
    }
    private FileRow file(ResultSet rs) throws SQLException { return new FileRow(rs.getLong("file_id"),rs.getLong("journal_id"),rs.getString("file_type"),rs.getString("original_file_name"),rs.getString("stored_file_name"),rs.getString("file_path"),rs.getString("mime_type"),(Long)rs.getObject("file_size"),rs.getInt("sort_order"),time(rs,"created_at")); }
    private static java.time.LocalDateTime time(ResultSet rs, String c) throws SQLException { var t=rs.getTimestamp(c); return t==null?null:t.toLocalDateTime(); }
}
