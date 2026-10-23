package com.example.backend.strategy;

import static com.example.backend.strategy.InvestmentStrategyDtos.*;

import com.example.backend.ContentFileOwnerType;
import com.example.backend.trade.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InvestmentStrategyService {
    private static final Set<String> SCOPES=Set.of("PORTFOLIO","THEME","STOCK"), STATUSES=Set.of("DRAFT","ACTIVE","PAUSED","CLOSED"), POST_TYPES=Set.of("NOTE","ANALYSIS","OPINION","REVIEW","CHANGE");
    private static final Map<String,Set<String>> STRATEGY_TYPES=Map.of(
            "PORTFOLIO",Set.of("INVESTMENT_PRINCIPLE","ASSET_ALLOCATION","MARKET_RESPONSE","RISK_MANAGEMENT","CASH_MANAGEMENT"),
            "THEME",Set.of("THEME_RESEARCH","THEME_INVESTMENT","THEME_TRADING"),
            "STOCK",Set.of("STOCK_RESEARCH","STOCK_TRADING","POSITION_MANAGEMENT","STOCK_RISK"));
    private static final Set<String> HORIZONS=Set.of("SHORT","MEDIUM","LONG","FLEXIBLE");
    private final InvestmentStrategyRepository repository;
    private final StrategyFileService fileService;
    public InvestmentStrategyService(InvestmentStrategyRepository repository,StrategyFileService fileService){this.repository=repository;this.fileService=fileService;}

    @Transactional(readOnly=true) public StrategyPage findAll(StrategyFilter f){return repository.findAll(f);}
    @Transactional(readOnly=true) public List<StrategyLink> links(){return repository.findLinks();}
    @Transactional(readOnly=true) public StrategyDetail findById(long id){var r=row(id);return detail(r);}
    @Transactional(readOnly=true) public ContentFile findFile(long id,long fileId){row(id);return repository.findFile(ContentFileOwnerType.STRATEGY,id,fileId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"첨부파일을 찾을 수 없습니다."));}
    @Transactional(readOnly=true) public ContentFile findPostFile(long id,long postId,long fileId){row(id);if(repository.findPost(id,postId).isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"게시물을 찾을 수 없습니다.");return repository.findFile(ContentFileOwnerType.STRATEGY_POST,postId,fileId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"첨부파일을 찾을 수 없습니다."));}
    @Transactional public StrategyDetail create(StrategySaveRequest raw,List<MultipartFile> files){var r=validate(raw);long id=repository.insert(r);repository.replaceStocks(id,r.stockIds());var content=fileService.save(ContentFileOwnerType.STRATEGY,id,r.content(),r.retainedFileIds(),r.newFiles(),files);repository.updateContent(id,content);return findById(id);}
    @Transactional public StrategyDetail update(long id,StrategySaveRequest raw,List<MultipartFile> files){var current=row(id);var r=validate(raw);var content=fileService.save(ContentFileOwnerType.STRATEGY,id,r.content(),r.retainedFileIds(),r.newFiles(),files);repository.update(id,new StrategySaveRequest(r.strategyScope(),r.strategyType(),r.investmentHorizon(),r.title(),content,r.status(),r.stockIds(),r.retainedFileIds(),r.newFiles()),current.versionNo()+1);repository.archive(current);repository.replaceStocks(id,r.stockIds());repository.deleteTradesOutsideStrategyStocks(id);return findById(id);}
    @Transactional public void delete(long id){row(id);if(repository.softDelete(id)==0)throw notFound();}
    @Transactional public StrategyPost createPost(long id,PostSaveRequest raw,List<MultipartFile> files){row(id);var r=validatePost(raw);long postId=repository.insertPost(id,r);var content=fileService.save(ContentFileOwnerType.STRATEGY_POST,postId,r.content(),r.retainedFileIds(),r.newFiles(),files);repository.updatePostContent(postId,content);return repository.findPost(id,postId).orElseThrow();}
    @Transactional public StrategyPost updatePost(long id,long postId,PostSaveRequest raw,List<MultipartFile> files){row(id);if(repository.findPost(id,postId).isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"게시물을 찾을 수 없습니다.");var r=validatePost(raw);var content=fileService.save(ContentFileOwnerType.STRATEGY_POST,postId,r.content(),r.retainedFileIds(),r.newFiles(),files);repository.updatePost(id,postId,new PostSaveRequest(r.postType(),r.title(),content,r.postDate(),r.retainedFileIds(),r.newFiles()));return repository.findPost(id,postId).orElseThrow();}
    @Transactional public void deletePost(long id,long postId){row(id);var post=repository.findPost(id,postId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"게시물을 찾을 수 없습니다."));repository.deleteFiles(ContentFileOwnerType.STRATEGY_POST,postId,List.of());if(repository.deletePost(id,postId)==0)throw new ApiException(HttpStatus.NOT_FOUND,"게시물을 찾을 수 없습니다.");fileService.deleteAfterCommit(post.files());}
    @Transactional public StrategyDetail restore(long id,long historyId){var current=row(id);var history=repository.findHistory(id,historyId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"변경이력을 찾을 수 없습니다."));repository.restore(id,history,current.versionNo()+1);repository.archive(current);return findById(id);}
    @Transactional public StrategyDetail replaceJournals(long id,List<Long> raw){row(id);List<Long> ids=raw==null?List.of():raw.stream().filter(Objects::nonNull).distinct().toList();if(!repository.journalsExist(ids))throw bad("연결할 수 없는 투자일지가 포함되어 있습니다.");repository.replaceJournals(id,ids);return findById(id);}
    @Transactional(readOnly=true) public List<RelatedTrade> findTradeCandidates(long id){row(id);return repository.findTradeCandidates(id);}
    @Transactional public StrategyDetail replaceTrades(long id,List<Long> raw){row(id);List<Long> ids=raw==null?List.of():raw.stream().filter(Objects::nonNull).distinct().toList();if(!repository.tradesBelongToStrategyStocks(id,ids))throw bad("관련 종목의 거래만 연결할 수 있습니다.");repository.replaceTrades(id,ids);return findById(id);}

    private StrategyDetail detail(StrategyRow r){return new StrategyDetail(r.strategyId(),r.strategyScope(),r.strategyType(),r.investmentHorizon(),r.title(),r.content(),r.status(),r.versionNo(),repository.findStocks(r.strategyId()),repository.findPosts(r.strategyId()),repository.findHistories(r.strategyId()),repository.findJournals(r.strategyId()),repository.findTrades(r.strategyId()),repository.findFiles(ContentFileOwnerType.STRATEGY,r.strategyId()),r.createdAt(),r.updatedAt());}
    private StrategyRow row(long id){return repository.findById(id).orElseThrow(this::notFound);}
    private StrategySaveRequest validate(StrategySaveRequest r){if(r==null)throw bad("요청 내용을 입력해 주세요.");String title=trim(r.title());if(title.isEmpty()||title.length()>200)throw bad("전략명은 1~200자로 입력해 주세요.");String scope=upper(r.strategyScope(),"PORTFOLIO");if(!SCOPES.contains(scope))throw bad("적용 범위가 올바르지 않습니다.");String strategyType=upper(r.strategyType(),"");if(!strategyType.isEmpty()&&!STRATEGY_TYPES.get(scope).contains(strategyType))throw bad("적용 범위에 맞는 전략 유형을 선택해 주세요.");String investmentHorizon=upper(r.investmentHorizon(),"");if(!investmentHorizon.isEmpty()&&!HORIZONS.contains(investmentHorizon))throw bad("투자 기간이 올바르지 않습니다.");String status=upper(r.status(),"DRAFT");if(!STATUSES.contains(status))throw bad("진행 상태가 올바르지 않습니다.");validDoc(r.content());List<Long> stocks=r.stockIds()==null?List.of():r.stockIds().stream().filter(Objects::nonNull).distinct().toList();if(!repository.stocksExist(stocks))throw bad("사용할 수 없는 관련 종목이 포함되어 있습니다.");if("STOCK".equals(scope)&&stocks.isEmpty())throw bad("종목 중심 전략은 관련 종목을 하나 이상 선택해 주세요.");return new StrategySaveRequest(scope,strategyType.isEmpty()?null:strategyType,investmentHorizon.isEmpty()?null:investmentHorizon,title,r.content(),status,stocks,r.retainedFileIds(),r.newFiles());}
    private PostSaveRequest validatePost(PostSaveRequest r){if(r==null)throw bad("게시물 내용을 입력해 주세요.");String title=trim(r.title());if(title.isEmpty()||title.length()>200)throw bad("제목은 1~200자로 입력해 주세요.");String type=upper(r.postType(),"NOTE");if(!POST_TYPES.contains(type))throw bad("게시물 유형이 올바르지 않습니다.");validDoc(r.content());return new PostSaveRequest(type,title,r.content(),r.postDate()==null?LocalDate.now():r.postDate(),r.retainedFileIds(),r.newFiles());}
    private void validDoc(JsonNode n){if(n==null||!n.isObject()||!"doc".equals(n.path("type").asText()))throw bad("본문은 Tiptap doc 형식이어야 합니다.");if(n.toString().getBytes(StandardCharsets.UTF_8).length>2_097_152)throw bad("본문 크기가 허용 범위를 초과했습니다.");}
    private String trim(String s){return s==null?"":s.trim();}private String upper(String s,String d){return s==null||s.isBlank()?d:s.trim().toUpperCase(Locale.ROOT);}private ApiException bad(String m){return new ApiException(HttpStatus.BAD_REQUEST,m);}private ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"투자전략을 찾을 수 없습니다.");}
}
