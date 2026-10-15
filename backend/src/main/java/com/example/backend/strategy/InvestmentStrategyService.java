package com.example.backend.strategy;

import static com.example.backend.strategy.InvestmentStrategyDtos.*;

import com.example.backend.trade.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentStrategyService {
    private static final Set<String> SCOPES=Set.of("GENERAL","STOCK"), STATUSES=Set.of("DRAFT","ACTIVE","PAUSED","CLOSED"), POST_TYPES=Set.of("NOTE","ANALYSIS","OPINION","REVIEW","CHANGE");
    private final InvestmentStrategyRepository repository;
    public InvestmentStrategyService(InvestmentStrategyRepository repository){this.repository=repository;}

    @Transactional(readOnly=true) public StrategyPage findAll(StrategyFilter f){return repository.findAll(f);}
    @Transactional(readOnly=true) public List<StrategyLink> links(){return repository.findLinks();}
    @Transactional(readOnly=true) public StrategyDetail findById(long id){var r=row(id);return detail(r);}
    @Transactional public StrategyDetail create(StrategySaveRequest raw){var r=validate(raw);long id=repository.insert(r);repository.replaceStocks(id,r.stockIds());return findById(id);}
    @Transactional public StrategyDetail update(long id,StrategySaveRequest raw){var current=row(id);var r=validate(raw);repository.update(id,r,current.versionNo()+1);repository.archive(current);repository.replaceStocks(id,r.stockIds());return findById(id);}
    @Transactional public void delete(long id){row(id);if(repository.softDelete(id)==0)throw notFound();}
    @Transactional public StrategyPost createPost(long id,PostSaveRequest raw){row(id);var r=validatePost(raw);return repository.findPost(id,repository.insertPost(id,r)).orElseThrow();}
    @Transactional public StrategyPost updatePost(long id,long postId,PostSaveRequest raw){row(id);var r=validatePost(raw);if(repository.updatePost(id,postId,r)==0)throw new ApiException(HttpStatus.NOT_FOUND,"게시물을 찾을 수 없습니다.");return repository.findPost(id,postId).orElseThrow();}
    @Transactional public void deletePost(long id,long postId){row(id);if(repository.deletePost(id,postId)==0)throw new ApiException(HttpStatus.NOT_FOUND,"게시물을 찾을 수 없습니다.");}
    @Transactional public StrategyDetail restore(long id,long historyId){var current=row(id);var history=repository.findHistory(id,historyId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"변경이력을 찾을 수 없습니다."));repository.restore(id,history,current.versionNo()+1);repository.archive(current);return findById(id);}
    @Transactional public StrategyDetail replaceJournals(long id,List<Long> raw){row(id);List<Long> ids=raw==null?List.of():raw.stream().filter(Objects::nonNull).distinct().toList();if(!repository.journalsExist(ids))throw bad("연결할 수 없는 투자일지가 포함되어 있습니다.");repository.replaceJournals(id,ids);return findById(id);}

    private StrategyDetail detail(StrategyRow r){return new StrategyDetail(r.strategyId(),r.strategyScope(),r.categoryCodes(),r.title(),r.content(),r.status(),r.versionNo(),repository.findStocks(r.strategyId()),repository.findPosts(r.strategyId()),repository.findHistories(r.strategyId()),repository.findJournals(r.strategyId()),repository.findTrades(r.strategyId()),r.createdAt(),r.updatedAt());}
    private StrategyRow row(long id){return repository.findById(id).orElseThrow(this::notFound);}
    private StrategySaveRequest validate(StrategySaveRequest r){if(r==null)throw bad("요청 내용을 입력해 주세요.");String title=trim(r.title());if(title.isEmpty()||title.length()>200)throw bad("전략명은 1~200자로 입력해 주세요.");String scope=upper(r.strategyScope(),"GENERAL");if(!SCOPES.contains(scope))throw bad("전략 구분이 올바르지 않습니다.");String status=upper(r.status(),"DRAFT");if(!STATUSES.contains(status))throw bad("진행 상태가 올바르지 않습니다.");validDoc(r.content());List<String> categories=r.categoryCodes()==null?List.of():r.categoryCodes().stream().filter(Objects::nonNull).map(String::trim).filter(x->!x.isEmpty()).distinct().toList();List<Long> stocks=r.stockIds()==null?List.of():r.stockIds().stream().filter(Objects::nonNull).distinct().toList();if(!repository.stocksExist(stocks))throw bad("사용할 수 없는 관련 종목이 포함되어 있습니다.");if("STOCK".equals(scope)&&stocks.isEmpty())throw bad("종목 중심 전략은 관련 종목을 하나 이상 선택해 주세요.");return new StrategySaveRequest(scope,categories,title,r.content(),status,stocks);}
    private PostSaveRequest validatePost(PostSaveRequest r){if(r==null)throw bad("게시물 내용을 입력해 주세요.");String title=trim(r.title());if(title.isEmpty()||title.length()>200)throw bad("제목은 1~200자로 입력해 주세요.");String type=upper(r.postType(),"NOTE");if(!POST_TYPES.contains(type))throw bad("게시물 유형이 올바르지 않습니다.");validDoc(r.content());return new PostSaveRequest(type,title,r.content(),r.postDate()==null?LocalDate.now():r.postDate());}
    private void validDoc(JsonNode n){if(n==null||!n.isObject()||!"doc".equals(n.path("type").asText()))throw bad("본문은 Tiptap doc 형식이어야 합니다.");if(n.toString().getBytes(StandardCharsets.UTF_8).length>2_097_152)throw bad("본문 크기가 허용 범위를 초과했습니다.");}
    private String trim(String s){return s==null?"":s.trim();}private String upper(String s,String d){return s==null||s.isBlank()?d:s.trim().toUpperCase(Locale.ROOT);}private ApiException bad(String m){return new ApiException(HttpStatus.BAD_REQUEST,m);}private ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"투자전략을 찾을 수 없습니다.");}
}
