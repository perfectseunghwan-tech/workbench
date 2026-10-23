package com.example.backend.analysis;

import static com.example.backend.analysis.AiAnalysisDtos.*;

import com.example.backend.journal.InvestmentJournalDtos.StoredFile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiAnalysisRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AiAnalysisRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Page search(LocalDateTime from, LocalDateTime to, String keyword, String type,
            String purpose, String source, String target, String tag, String review, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (from != null) { where.append(" AND a.analyzed_at>=:from"); p.addValue("from", from); }
        if (to != null) { where.append(" AND a.analyzed_at<:to"); p.addValue("to", to.plusDays(1)); }
        if (keyword != null && !keyword.isBlank()) { where.append(" AND (a.title ILIKE :keyword OR a.content ILIKE :keyword)"); p.addValue("keyword", "%" + keyword.trim() + "%"); }
        if (type != null && !type.isBlank()) { where.append(" AND a.analysis_type_code=:type"); p.addValue("type", type); }
        if (purpose != null && !purpose.isBlank()) { where.append(" AND a.analysis_purpose_code=:purpose"); p.addValue("purpose", purpose); }
        if (source != null && !source.isBlank()) { where.append(" AND a.source_code=:source"); p.addValue("source", source); }
        if (review != null && !review.isBlank()) { where.append(" AND a.review_status_code=:review"); p.addValue("review", review); }
        if (target != null && !target.isBlank()) { where.append(" AND EXISTS(SELECT 1 FROM ai_analysis_target t WHERE t.analysis_id=a.analysis_id AND (t.target_name ILIKE :target OR t.target_code ILIKE :target))"); p.addValue("target", "%" + target.trim() + "%"); }
        if (tag != null && !tag.isBlank()) { where.append(" AND EXISTS(SELECT 1 FROM ai_analysis_tag t WHERE t.analysis_id=a.analysis_id AND t.tag_name ILIKE :tag)"); p.addValue("tag", "%" + tag.trim() + "%"); }
        String base = " FROM ai_analysis a" + where;
        Long total = jdbc.queryForObject("SELECT count(*)" + base, p, Long.class);
        p.addValue("limit", size).addValue("offset", page * size);
        List<Summary> rows = jdbc.query("SELECT a.analysis_id,a.title,a.analysis_type_code,a.analysis_purpose_code,a.source_code,a.opinion_code,a.review_status_code,a.analyzed_at" + base + " ORDER BY a.analyzed_at DESC,a.analysis_id DESC LIMIT :limit OFFSET :offset", p,
                (rs, n) -> new Summary(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), time(rs,"analyzed_at"), targets(rs.getLong(1)), tags(rs.getLong(1))));
        long count = total == null ? 0 : total;
        return new Page(rows, count, (int) Math.ceil((double) count / size), page, size);
    }

    public Detail find(long id) {
        return jdbc.query("SELECT * FROM ai_analysis WHERE analysis_id=:id", Map.of("id", id), (rs,n) ->
                new Detail(id, rs.getString("title"), rs.getString("analysis_type_code"), rs.getString("analysis_purpose_code"),
                    rs.getString("summary"), rs.getString("content"), rs.getString("content_format"), rs.getString("conclusion"),
                    rs.getString("key_risks"), rs.getString("source_code"),
                    rs.getString("source_url"), time(rs,"analyzed_at"), time(rs,"data_as_of"), rs.getString("opinion_code"),
                    rs.getString("review_status_code"), rs.getString("user_review"), time(rs,"reviewed_at"),
                    targets(id), relations(id), tags(id), files(id))).stream().findFirst().orElse(null);
    }
    public long insert(SaveRequest r) {
        Long id = jdbc.queryForObject("""
                INSERT INTO ai_analysis(title,analysis_type_code,analysis_purpose_code,summary,content,content_format,conclusion,key_risks,source_code,source_url,analyzed_at,data_as_of,opinion_code,review_status_code,user_review,reviewed_at)
                VALUES(:title,:type,:purpose,:summary,:content,:format,:conclusion,:risks,:source,:url,:analyzed,:dataAsOf,:opinion,:review,:userReview,:reviewedAt) RETURNING analysis_id
                """, params(r), Long.class);
        return id;
    }
    public void update(long id, SaveRequest r) {
        jdbc.update("""
                UPDATE ai_analysis SET title=:title,analysis_type_code=:type,analysis_purpose_code=:purpose,summary=:summary,content=:content,content_format=:format,conclusion=:conclusion,key_risks=:risks,source_code=:source,source_url=:url,analyzed_at=:analyzed,data_as_of=:dataAsOf,opinion_code=:opinion,review_status_code=:review,user_review=:userReview,reviewed_at=:reviewedAt,updated_at=CURRENT_TIMESTAMP WHERE analysis_id=:id
                """, params(r).addValue("id", id));
    }
    public void updateContent(long id,String content) { jdbc.update("UPDATE ai_analysis SET content=:content WHERE analysis_id=:id",Map.of("id",id,"content",content)); }
    private MapSqlParameterSource params(SaveRequest r) {
        return new MapSqlParameterSource().addValue("title",r.title()).addValue("type",r.analysisTypeCode()).addValue("purpose",r.analysisPurposeCode())
                .addValue("summary",r.summary()).addValue("content",r.content()).addValue("format",r.contentFormat())
                .addValue("conclusion",r.conclusion()).addValue("risks",r.keyRisks()).addValue("source",r.sourceCode())
                .addValue("url",r.sourceUrl())
                .addValue("analyzed",r.analyzedAt()).addValue("dataAsOf",r.dataAsOf()).addValue("opinion",r.opinionCode())
                .addValue("review",r.reviewStatusCode()).addValue("userReview",r.userReview())
                .addValue("reviewedAt","UNREVIEWED".equals(r.reviewStatusCode()) ? null : LocalDateTime.now());
    }
    public void replaceChildren(long id, SaveRequest r) {
        jdbc.update("DELETE FROM ai_analysis_target WHERE analysis_id=:id", Map.of("id",id));
        jdbc.update("DELETE FROM ai_analysis_relation WHERE analysis_id=:id", Map.of("id",id));
        jdbc.update("DELETE FROM ai_analysis_tag WHERE analysis_id=:id", Map.of("id",id));
        for (int i=0;i<r.targets().size();i++) { Target t=r.targets().get(i); jdbc.update("INSERT INTO ai_analysis_target(analysis_id,target_type_code,target_code,target_name,is_primary,sort_order) VALUES(:id,:type,:code,:name,:primary,:sort)", new MapSqlParameterSource().addValue("id",id).addValue("type",t.targetTypeCode()).addValue("code",t.targetCode()).addValue("name",t.targetName()).addValue("primary",t.isPrimary()).addValue("sort",i)); }
        for (Relation x:r.relations()) jdbc.update("INSERT INTO ai_analysis_relation(analysis_id,relation_type_code,related_id,relation_note) VALUES(:id,:type,:related,:note)", new MapSqlParameterSource().addValue("id",id).addValue("type",x.relationTypeCode()).addValue("related",x.relatedId()).addValue("note",x.relationNote()));
        for (String tag:r.tags()) jdbc.update("INSERT INTO ai_analysis_tag(analysis_id,tag_name) VALUES(:id,:tag)", Map.of("id",id,"tag",tag));
    }
    public List<Target> targets(long id) { return jdbc.query("SELECT target_type_code,target_code,target_name,is_primary FROM ai_analysis_target WHERE analysis_id=:id ORDER BY sort_order,target_id",Map.of("id",id),(rs,n)->new Target(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4))); }
    public List<String> tags(long id) { return jdbc.query("SELECT tag_name FROM ai_analysis_tag WHERE analysis_id=:id ORDER BY tag_id",Map.of("id",id),(rs,n)->rs.getString(1)); }
    public List<Relation> relations(long id) { return jdbc.query("SELECT relation_type_code,related_id,relation_note FROM ai_analysis_relation WHERE analysis_id=:id ORDER BY relation_id",Map.of("id",id),(rs,n)->new Relation(rs.getString(1),rs.getLong(2),rs.getString(3),relatedTitle(rs.getString(1),rs.getLong(2)))); }
    private String relatedTitle(String type,long id) {
        String[] tables = switch(type) { case "STRATEGY" -> new String[]{"investment_strategy","strategy_id","title"}; case "JOURNAL" -> new String[]{"investment_journal","journal_id","journal_title"}; case "TRADE" -> new String[]{"stock_trade","trade_id","trade_date::text"}; case "ACCOUNT" -> new String[]{"investment_account","account_id","account_name"}; case "ANALYSIS" -> new String[]{"ai_analysis","analysis_id","title"}; default -> null; };
        if(tables==null) return null;
        return jdbc.query("SELECT " + tables[2] + " FROM " + tables[0] + " WHERE " + tables[1] + "=:id",Map.of("id",id),(rs,n)->rs.getString(1)).stream().findFirst().orElse(null);
    }
    public boolean relationExists(String type,long id) {
        String[] table = switch(type) { case "STRATEGY" -> new String[]{"investment_strategy","strategy_id"}; case "JOURNAL" -> new String[]{"investment_journal","journal_id"}; case "TRADE" -> new String[]{"stock_trade","trade_id"}; case "ACCOUNT" -> new String[]{"investment_account","account_id"}; case "ANALYSIS" -> new String[]{"ai_analysis","analysis_id"}; default -> null; };
        if(table==null) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table[0]+" WHERE "+table[1]+"=:id)",Map.of("id",id),Boolean.class));
    }
    public List<RelatedOption> relatedOptions(String type,String keyword) {
        String[] table = switch(type) { case "STRATEGY" -> new String[]{"investment_strategy","strategy_id","title"}; case "JOURNAL" -> new String[]{"investment_journal","journal_id","COALESCE(journal_title,'제목 없음')"}; case "TRADE" -> new String[]{"stock_trade","trade_id","trade_date::text || ' · ' || trade_type"}; case "ACCOUNT" -> new String[]{"investment_account","account_id","account_name"}; case "ANALYSIS" -> new String[]{"ai_analysis","analysis_id","title"}; default -> null; };
        if(table==null) return List.of();
        return jdbc.query("SELECT "+table[1]+" AS id,"+table[2]+" AS title FROM "+table[0]+" WHERE ("+table[2]+") ILIKE :keyword ORDER BY "+table[1]+" DESC LIMIT 30",Map.of("keyword","%"+(keyword==null?"":keyword.trim())+"%"),(rs,n)->new RelatedOption(rs.getLong(1),rs.getString(2)));
    }
    public List<RelatedOption> stockOptions(String keyword) {
        return jdbc.query("SELECT stock_id,stock_name || ' (' || stock_code || ')' FROM stock_master WHERE use_yn=TRUE AND (stock_name ILIKE :keyword OR stock_code ILIKE :keyword) ORDER BY stock_name LIMIT 30",Map.of("keyword","%"+(keyword==null?"":keyword.trim())+"%"),(rs,n)->new RelatedOption(rs.getLong(1),rs.getString(2)));
    }
    public List<FileInfo> files(long id) { return jdbc.query("SELECT file_id,file_type,original_file_name,file_path,mime_type,file_size,sort_order FROM investment_content_file WHERE owner_type='AI_ANALYSIS' AND owner_id=:id ORDER BY sort_order,file_id",Map.of("id",id),(rs,n)->new FileInfo(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),(Long)rs.getObject(6),rs.getInt(7),"/api/ai-analyses/"+id+"/files/"+rs.getLong(1))); }
    public long insertFile(long id, StoredFile f) { return jdbc.queryForObject("INSERT INTO investment_content_file(owner_type,owner_id,file_type,original_file_name,stored_file_name,file_path,mime_type,file_size,sort_order) VALUES('AI_ANALYSIS',:id,:type,:original,:stored,:path,:mime,:size,:sort) RETURNING file_id",new MapSqlParameterSource().addValue("id",id).addValue("type",f.fileType()).addValue("original",f.originalFileName()).addValue("stored",f.storedFileName()).addValue("path",f.filePath()).addValue("mime",f.mimeType()).addValue("size",f.fileSize()).addValue("sort",f.sortOrder()),Long.class); }
    public void updateFileOrder(long id,List<Long> fileIds) {
        for(int i=0;i<fileIds.size();i++) jdbc.update("UPDATE investment_content_file SET sort_order=:sort WHERE owner_type='AI_ANALYSIS' AND owner_id=:id AND file_id=:fileId",Map.of("sort",i,"id",id,"fileId",fileIds.get(i)));
    }
    public void deleteFiles(long id,List<Long> keep) { if(keep.isEmpty()) jdbc.update("DELETE FROM investment_content_file WHERE owner_type='AI_ANALYSIS' AND owner_id=:id",Map.of("id",id)); else jdbc.update("DELETE FROM investment_content_file WHERE owner_type='AI_ANALYSIS' AND owner_id=:id AND file_id NOT IN (:keep)",Map.of("id",id,"keep",keep)); }
    public void removeIncoming(long id) { jdbc.update("DELETE FROM ai_analysis_relation WHERE relation_type_code='ANALYSIS' AND related_id=:id",Map.of("id",id)); }
    public void delete(long id) { jdbc.update("DELETE FROM ai_analysis WHERE analysis_id=:id",Map.of("id",id)); }
    private static LocalDateTime time(ResultSet rs,String name) throws SQLException { var value=rs.getTimestamp(name); return value==null?null:value.toLocalDateTime(); }
}
