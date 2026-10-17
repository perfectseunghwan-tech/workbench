package com.example.backend.strategy;

import static com.example.backend.strategy.InvestmentStrategyDtos.*;

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
public class InvestmentStrategyRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public InvestmentStrategyRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.mapper = mapper;
    }

    public StrategyPage findAll(StrategyFilter filter) {
        Map<String,Object> p = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE s.use_yn=TRUE");
        if (text(filter.keyword())) { where.append(" AND (LOWER(s.title) LIKE :keyword OR LOWER(s.content::text) LIKE :keyword OR EXISTS (SELECT 1 FROM investment_strategy_stock x JOIN stock_master sm ON sm.stock_id=x.stock_id WHERE x.strategy_id=s.strategy_id AND LOWER(sm.stock_name) LIKE :keyword))"); p.put("keyword", "%"+filter.keyword().trim().toLowerCase(Locale.ROOT)+"%"); }
        if (text(filter.strategyScope())) { where.append(" AND s.strategy_scope=:scope"); p.put("scope",filter.strategyScope()); }
        if (text(filter.categoryCode())) { where.append(" AND :category=ANY(s.category_codes)"); p.put("category",filter.categoryCode()); }
        if (text(filter.status())) { where.append(" AND s.status=:status"); p.put("status",filter.status()); }
        p.put("limit",filter.size()); p.put("offset",filter.page()*filter.size());
        String column = switch (filter.sortField()==null?"":filter.sortField()) { case "title" -> "s.title"; case "strategyScope" -> "s.strategy_scope"; case "status" -> "s.status"; case "createdAt" -> "s.created_at"; default -> "latest_activity_at"; };
        String direction = "asc".equalsIgnoreCase(filter.sortDirection()) ? "ASC" : "DESC";
        String order = column + " " + direction + " NULLS LAST";
        String select = """
                SELECT s.strategy_id,s.strategy_scope,s.category_codes,s.title,s.content,s.status,s.version_no,s.created_at,s.updated_at,
                       GREATEST(s.updated_at,COALESCE((SELECT MAX(p.updated_at) FROM investment_strategy_post p WHERE p.strategy_id=s.strategy_id),s.updated_at)) latest_activity_at
                  FROM investment_strategy s
                """;
        List<StrategySummary> content = jdbc.query(select+where+" ORDER BY "+order+",s.strategy_id DESC LIMIT :limit OFFSET :offset",p,(rs,n)->new StrategySummary(
                rs.getLong("strategy_id"),rs.getString("strategy_scope"),array(rs,"category_codes"),rs.getString("title"),rs.getString("status"),rs.getInt("version_no"),
                findStocks(rs.getLong("strategy_id")),time(rs,"latest_activity_at"),time(rs,"created_at"),time(rs,"updated_at")));
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM investment_strategy s"+where,p,Long.class); long count=total==null?0:total;
        return new StrategyPage(content,count,count==0?0:(int)Math.ceil((double)count/filter.size()),filter.page(),filter.size());
    }

    public Optional<StrategyRow> findById(long id) {
        return jdbc.query("SELECT strategy_id,strategy_scope,category_codes,title,content,status,version_no,created_at,updated_at FROM investment_strategy WHERE strategy_id=:id AND use_yn=TRUE",Map.of("id",id),this::row).stream().findFirst();
    }
    public long insert(StrategySaveRequest r) {
        Long id=jdbc.queryForObject("""
                INSERT INTO investment_strategy(strategy_scope,category_codes,title,content,status)
                VALUES(:scope,:categories,:title,CAST(:content AS jsonb),:status) RETURNING strategy_id
                """,params(r),Long.class); if(id==null)throw new IllegalStateException("전략 ID를 생성하지 못했습니다."); return id;
    }
    public int update(long id,StrategySaveRequest r,int nextVersion) {
        return jdbc.update("UPDATE investment_strategy SET strategy_scope=:scope,category_codes=:categories,title=:title,content=CAST(:content AS jsonb),status=:status,version_no=:version,updated_at=CURRENT_TIMESTAMP WHERE strategy_id=:id AND use_yn=TRUE",
                params(r).addValue("id",id).addValue("version",nextVersion));
    }
    public int restore(long id,StrategyHistory h,int nextVersion) {
        return jdbc.update("UPDATE investment_strategy SET title=:title,content=CAST(:content AS jsonb),version_no=:version,updated_at=CURRENT_TIMESTAMP WHERE strategy_id=:id AND use_yn=TRUE",
                new MapSqlParameterSource().addValue("id",id).addValue("title",h.title()).addValue("content",json(h.content())).addValue("version",nextVersion));
    }
    public void archive(StrategyRow r) {
        jdbc.update("INSERT INTO investment_strategy_history(strategy_id,version_no,title,content) VALUES(:id,:version,:title,CAST(:content AS jsonb)) ON CONFLICT(strategy_id,version_no) DO NOTHING",
                new MapSqlParameterSource().addValue("id",r.strategyId()).addValue("version",r.versionNo()).addValue("title",r.title()).addValue("content",json(r.content())));
    }
    public int softDelete(long id){return jdbc.update("UPDATE investment_strategy SET use_yn=FALSE,updated_at=CURRENT_TIMESTAMP WHERE strategy_id=:id AND use_yn=TRUE",Map.of("id",id));}
    public void replaceStocks(long id,List<Long> ids){jdbc.update("DELETE FROM investment_strategy_stock WHERE strategy_id=:id",Map.of("id",id));for(Long stockId:ids)jdbc.update("INSERT INTO investment_strategy_stock(strategy_id,stock_id) VALUES(:id,:stockId)",Map.of("id",id,"stockId",stockId));}
    public boolean stocksExist(List<Long> ids){if(ids.isEmpty())return true;Long count=jdbc.queryForObject("SELECT COUNT(*) FROM stock_master WHERE use_yn=TRUE AND stock_id IN (:ids)",Map.of("ids",ids),Long.class);return count!=null&&count==new HashSet<>(ids).size();}
    public List<StockItem> findStocks(long id){return jdbc.query("SELECT m.stock_id,m.stock_code,m.stock_name,m.market_code,m.currency_code FROM investment_strategy_stock x JOIN stock_master m ON m.stock_id=x.stock_id WHERE x.strategy_id=:id ORDER BY m.stock_name",Map.of("id",id),(rs,n)->new StockItem(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)));}
    public List<StrategyPost> findPosts(long id){return jdbc.query("SELECT post_id,post_type,title,content,post_date,created_at,updated_at FROM investment_strategy_post WHERE strategy_id=:id ORDER BY post_date DESC,post_id DESC",Map.of("id",id),(rs,n)->new StrategyPost(rs.getLong("post_id"),rs.getString("post_type"),rs.getString("title"),tree(rs,"content"),rs.getObject("post_date",java.time.LocalDate.class),time(rs,"created_at"),time(rs,"updated_at")));}
    public Optional<StrategyPost> findPost(long id,long postId){return jdbc.query("SELECT post_id,post_type,title,content,post_date,created_at,updated_at FROM investment_strategy_post WHERE strategy_id=:id AND post_id=:postId",Map.of("id",id,"postId",postId),(rs,n)->new StrategyPost(rs.getLong("post_id"),rs.getString("post_type"),rs.getString("title"),tree(rs,"content"),rs.getObject("post_date",java.time.LocalDate.class),time(rs,"created_at"),time(rs,"updated_at"))).stream().findFirst();}
    public long insertPost(long id,PostSaveRequest r){Long postId=jdbc.queryForObject("INSERT INTO investment_strategy_post(strategy_id,post_type,title,content,post_date) VALUES(:id,:type,:title,CAST(:content AS jsonb),:date) RETURNING post_id",postParams(id,r),Long.class);return Objects.requireNonNull(postId);}
    public int updatePost(long id,long postId,PostSaveRequest r){return jdbc.update("UPDATE investment_strategy_post SET post_type=:type,title=:title,content=CAST(:content AS jsonb),post_date=:date,updated_at=CURRENT_TIMESTAMP WHERE strategy_id=:id AND post_id=:postId",postParams(id,r).addValue("postId",postId));}
    public int deletePost(long id,long postId){return jdbc.update("DELETE FROM investment_strategy_post WHERE strategy_id=:id AND post_id=:postId",Map.of("id",id,"postId",postId));}
    public List<StrategyHistory> findHistories(long id){return jdbc.query("SELECT history_id,version_no,title,content,archived_at FROM investment_strategy_history WHERE strategy_id=:id ORDER BY version_no DESC",Map.of("id",id),(rs,n)->new StrategyHistory(rs.getLong("history_id"),rs.getInt("version_no"),rs.getString("title"),tree(rs,"content"),time(rs,"archived_at")));}
    public Optional<StrategyHistory> findHistory(long id,long historyId){return findHistories(id).stream().filter(x->x.historyId()==historyId).findFirst();}
    public List<LinkedJournal> findJournals(long id){return jdbc.query("SELECT j.journal_id,j.journal_date,j.journal_title,j.updated_at FROM investment_strategy_journal x JOIN investment_journal j ON j.journal_id=x.journal_id WHERE x.strategy_id=:id ORDER BY j.journal_date DESC,j.journal_id DESC",Map.of("id",id),(rs,n)->new LinkedJournal(rs.getLong(1),rs.getObject(2,java.time.LocalDate.class),rs.getString(3),time(rs,"updated_at")));}
    public boolean journalsExist(List<Long> ids){if(ids.isEmpty())return true;Long count=jdbc.queryForObject("SELECT COUNT(*) FROM investment_journal WHERE journal_id IN (:ids)",Map.of("ids",ids),Long.class);return count!=null&&count==new HashSet<>(ids).size();}
    public void replaceJournals(long id,List<Long> ids){jdbc.update("DELETE FROM investment_strategy_journal WHERE strategy_id=:id",Map.of("id",id));for(Long journalId:ids)jdbc.update("INSERT INTO investment_strategy_journal(strategy_id,journal_id) VALUES(:id,:journalId)",Map.of("id",id,"journalId",journalId));}
    public List<RelatedTrade> findTrades(long id){return jdbc.query("""
            SELECT t.trade_id,t.trade_date,t.trade_type,m.stock_id,m.stock_code,m.stock_name,b.brokerage_name,a.account_name,t.execution_quantity,t.execution_price,t.execution_amount,t.realized_profit_loss,t.trade_memo
              FROM investment_strategy_stock x JOIN stock_master m ON m.stock_id=x.stock_id JOIN stock_trade t ON t.stock_id=x.stock_id
              JOIN investment_account a ON a.account_id=t.account_id JOIN brokerage_company b ON b.brokerage_id=a.brokerage_id
             WHERE x.strategy_id=:id ORDER BY t.trade_date DESC,t.trade_id DESC
            """,Map.of("id",id),(rs,n)->new RelatedTrade(rs.getLong(1),rs.getObject(2,java.time.LocalDate.class),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getBigDecimal(9),rs.getBigDecimal(10),rs.getBigDecimal(11),rs.getBigDecimal(12),rs.getString(13)));}
    public List<StrategyLink> findLinks(){return jdbc.query("SELECT strategy_id,title,status FROM investment_strategy WHERE use_yn=TRUE ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'DRAFT' THEN 1 ELSE 2 END,title",Map.of(),(rs,n)->new StrategyLink(rs.getLong(1),rs.getString(2),rs.getString(3)));}

    private MapSqlParameterSource params(StrategySaveRequest r){return new MapSqlParameterSource().addValue("scope",r.strategyScope()).addValue("categories",r.categoryCodes().toArray(String[]::new)).addValue("title",r.title()).addValue("content",json(r.content())).addValue("status",r.status());}
    private MapSqlParameterSource postParams(long id,PostSaveRequest r){return new MapSqlParameterSource().addValue("id",id).addValue("type",r.postType()).addValue("title",r.title()).addValue("content",json(r.content())).addValue("date",r.postDate());}
    private StrategyRow row(ResultSet rs,int n)throws SQLException{return new StrategyRow(rs.getLong("strategy_id"),rs.getString("strategy_scope"),array(rs,"category_codes"),rs.getString("title"),tree(rs,"content"),rs.getString("status"),rs.getInt("version_no"),time(rs,"created_at"),time(rs,"updated_at"));}
    private List<String> array(ResultSet rs,String c)throws SQLException{String[] v=(String[])rs.getArray(c).getArray();return List.of(v);}
    private JsonNode tree(ResultSet rs,String c)throws SQLException{try{return mapper.readTree(rs.getString(c));}catch(JsonProcessingException e){throw new SQLException("잘못된 JSON",e);}}
    private String json(JsonNode n){try{return mapper.writeValueAsString(n);}catch(JsonProcessingException e){throw new IllegalArgumentException("본문을 직렬화할 수 없습니다.",e);}}
    private static java.time.LocalDateTime time(ResultSet rs,String c)throws SQLException{var t=rs.getTimestamp(c);return t==null?null:t.toLocalDateTime();}
    private boolean text(String s){return s!=null&&!s.isBlank();}
}
