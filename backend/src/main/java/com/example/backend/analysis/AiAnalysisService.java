package com.example.backend.analysis;

import static com.example.backend.analysis.AiAnalysisDtos.*;

import com.example.backend.journal.InvestmentJournalDtos.StoredFile;
import com.example.backend.journal.JournalFileStorageService;
import com.example.backend.trade.ApiException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AiAnalysisService {
    private final AiAnalysisRepository repository;
    private final JournalFileStorageService storage;
    public AiAnalysisService(AiAnalysisRepository repository, JournalFileStorageService storage) { this.repository=repository; this.storage=storage; }
    @Transactional(readOnly=true) public Page search(LocalDateTime from,LocalDateTime to,String keyword,String type,String purpose,String source,String target,String tag,String review,int page,int size) {
        if(from!=null&&to!=null&&from.isAfter(to)) throw bad("분석 기간이 올바르지 않습니다.");
        return repository.search(from,to,keyword,type,purpose,source,target,tag,review,Math.max(0,page),Math.min(100,Math.max(1,size)));
    }
    @Transactional(readOnly=true) public Detail find(long id) { Detail value=repository.find(id); if(value==null) throw new ApiException(HttpStatus.NOT_FOUND,"AI 분석을 찾을 수 없습니다."); return value; }
    @Transactional public Detail create(SaveRequest raw,List<MultipartFile> files) { SaveRequest r=validate(raw,0,files); long id=repository.insert(r); repository.replaceChildren(id,r); Map<String,Long> uploaded=saveFiles(id,r,files,List.of()); if("HTML".equals(r.contentFormat())) repository.updateContent(id,resolveImages(id,r.content(),uploaded)); return find(id); }
    @Transactional public Detail update(long id,SaveRequest raw,List<MultipartFile> files) { find(id); SaveRequest r=validate(raw,id,files); List<FileInfo> old=repository.files(id); repository.update(id,r); repository.replaceChildren(id,r); Map<String,Long> uploaded=saveFiles(id,r,files,old); if("HTML".equals(r.contentFormat())) repository.updateContent(id,resolveImages(id,r.content(),uploaded)); return find(id); }
    @Transactional public void delete(long id) { find(id); List<FileInfo> old=repository.files(id); repository.deleteFiles(id,List.of()); repository.removeIncoming(id); repository.delete(id); afterCommit(()->old.forEach(f->storage.delete(f.filePath()))); }
    @Transactional(readOnly=true) public FileInfo file(long id,long fileId) { find(id); return repository.files(id).stream().filter(f->f.fileId()==fileId).findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"첨부파일을 찾을 수 없습니다.")); }
    @Transactional(readOnly=true) public List<RelatedOption> relatedOptions(String type,String keyword) { return repository.relatedOptions(type,keyword); }
    @Transactional(readOnly=true) public List<RelatedOption> stockOptions(String keyword) { return repository.stockOptions(keyword); }
    private SaveRequest validate(SaveRequest r,long id,List<MultipartFile> files) {
        if(r==null||r.title()==null||r.title().isBlank()||r.title().length()>300) throw bad("제목은 1~300자로 입력해 주세요.");
        if(r.analyzedAt()==null) throw bad("분석일시를 입력해 주세요.");
        if(r.analysisTypeCode()==null||!Set.of("STOCK","MARKET","INDUSTRY","PORTFOLIO","STRATEGY","MACRO","ISSUE","COMPARISON").contains(r.analysisTypeCode())) throw bad("분석 유형이 올바르지 않습니다.");
        if(r.sourceCode()==null||!Set.of("CHATGPT_APP","CLAUDE_APP","GPT_API","CLAUDE_API","OTHER_AI").contains(r.sourceCode())) throw bad("분석 출처가 올바르지 않습니다.");
        if(r.sourceUrl()!=null&&!r.sourceUrl().isBlank()&&(!r.sourceUrl().matches("(?i)^https?://[^\\s]+$")||r.sourceUrl().length()>2000)) throw bad("원문 링크는 HTTP 또는 HTTPS 주소로 입력해 주세요.");
        if(r.analysisPurposeCode()!=null&&!r.analysisPurposeCode().isBlank()&&!Set.of("GENERAL","NEW_BUY","ADD_BUY","HOLD","SELL","MID_LONG_TERM","RISK_REVIEW","REGULAR_REVIEW").contains(r.analysisPurposeCode())) throw bad("분석 목적이 올바르지 않습니다.");
        if(r.opinionCode()!=null&&!r.opinionCode().isBlank()&&!Set.of("BUY","ADD","HOLD","WAIT","REDUCE","SELL","NEUTRAL","UNDETERMINED").contains(r.opinionCode())) throw bad("투자 의견이 올바르지 않습니다.");
        String review=r.reviewStatusCode()==null?"UNREVIEWED":r.reviewStatusCode();
        if(!Set.of("UNREVIEWED","REVIEWED","ADOPTED","PARTIAL","REJECTED").contains(review)) throw bad("검토 상태가 올바르지 않습니다.");
        String format=r.contentFormat()==null?"HTML":r.contentFormat().toUpperCase(Locale.ROOT);
        if(!Set.of("HTML","MARKDOWN","TEXT").contains(format)||r.content()==null||r.content().isBlank()||r.content().length()>2_000_000) throw bad("분석 본문이 올바르지 않습니다.");
        String content="HTML".equals(format)?Jsoup.clean(r.content(),Safelist.relaxed().addTags("h1","h2","table","thead","tbody","tr","th","td").addAttributes("td","colspan","rowspan").addAttributes("th","colspan","rowspan").addAttributes("img","data-upload-key").preserveRelativeLinks(true)):r.content();
        if(content.isBlank()) throw bad("분석 본문을 입력해 주세요.");
        List<Target> targets=r.targets()==null?List.of():r.targets();
        if(targets.stream().filter(Target::isPrimary).count()>1) throw bad("대표 대상은 하나만 선택할 수 있습니다.");
        for(Target t:targets) if(t==null||t.targetTypeCode()==null||!Set.of("STOCK","ETF","MARKET","INDUSTRY","PORTFOLIO","MACRO","OTHER").contains(t.targetTypeCode())||t.targetName()==null||t.targetName().isBlank()||t.targetName().length()>200||(t.targetCode()!=null&&t.targetCode().length()>100)) throw bad("분석 대상이 올바르지 않습니다.");
        List<Relation> relations=r.relations()==null?List.of():r.relations(); Set<String> uniqueRelations=new HashSet<>();
        for(Relation x:relations) if(x==null||x.relationTypeCode()==null||!Set.of("STRATEGY","JOURNAL","TRADE","ACCOUNT","ANALYSIS").contains(x.relationTypeCode())||x.relatedId()<=0||(x.relationTypeCode().equals("ANALYSIS")&&x.relatedId()==id)||!uniqueRelations.add(x.relationTypeCode()+":"+x.relatedId())||!repository.relationExists(x.relationTypeCode(),x.relatedId())) throw bad("연결할 수 없는 관련 데이터가 포함되어 있습니다.");
        if(relations.stream().anyMatch(x->x.relationNote()!=null&&x.relationNote().length()>500)) throw bad("관계 메모는 500자 이하여야 합니다.");
        List<String> tags=r.tags()==null?List.of():r.tags().stream().filter(x->x!=null&&!x.isBlank()).map(x->x.replaceFirst("^#","").trim().replaceAll("\\s+"," ")).distinct().toList();
        if(tags.stream().anyMatch(x->x.length()>100)) throw bad("태그는 100자 이하여야 합니다.");
        List<Long> keep=r.retainedFileIds()==null?List.of():r.retainedFileIds(); List<Long> existing=repository.files(id).stream().map(FileInfo::fileId).toList();
        if(keep.stream().anyMatch(x->x==null)||keep.size()!=new HashSet<>(keep).size()||!existing.containsAll(keep)) throw bad("첨부파일 정보가 올바르지 않습니다.");
        var manifests=r.newFiles()==null?List.<com.example.backend.journal.InvestmentJournalDtos.NewFileManifest>of():r.newFiles();
        if(manifests.size()!=files.size()||keep.size()+files.size()>30||files.stream().mapToLong(MultipartFile::getSize).sum()>104857600L) throw bad("첨부파일 수 또는 크기가 허용 범위를 초과했습니다.");
        Set<String> uploadKeys=new HashSet<>();
        for(var manifest:manifests) if(manifest==null||manifest.uploadKey()==null||manifest.uploadKey().isBlank()||!uploadKeys.add(manifest.uploadKey())) throw bad("첨부파일 식별자가 올바르지 않습니다.");
        return new SaveRequest(r.title().trim(),r.analysisTypeCode(),r.analysisPurposeCode(),r.summary(),content,format,r.conclusion(),r.keyRisks(),r.sourceCode(),r.sourceUrl(),r.analyzedAt(),r.dataAsOf(),r.opinionCode(),review,r.userReview(),targets,relations,tags,keep,manifests);
    }
    private Map<String,Long> saveFiles(long id,SaveRequest r,List<MultipartFile> files,List<FileInfo> existing) {
        List<StoredFile> staged=new ArrayList<>(); List<FileInfo> removed=existing.stream().filter(f->!r.retainedFileIds().contains(f.fileId())).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){removed.forEach(f->storage.delete(f.filePath()));}
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED) storage.deleteStored(staged);}
        });
        List<Long> keep=new ArrayList<>(r.retainedFileIds()); Map<String,Long> uploaded=new HashMap<>();
        for(int i=0;i<files.size();i++) { StoredFile f=storage.store("ai-analyses/"+id,files.get(i),r.newFiles().get(i)); staged.add(f); long fileId=repository.insertFile(id,f); keep.add(fileId); uploaded.put(r.newFiles().get(i).uploadKey(),fileId); }
        repository.deleteFiles(id,keep);
        staged.forEach(storage::promote);
        return uploaded;
    }
    private String resolveImages(long id,String content,Map<String,Long> uploaded) {
        Document doc=Jsoup.parseBodyFragment(content);
        for(var image:doc.select("img[data-upload-key]")) {
            String key=image.attr("data-upload-key"); Long fileId=uploaded.get(key);
            if(fileId==null) throw bad("본문 이미지와 업로드 파일이 일치하지 않습니다.");
            image.removeAttr("data-upload-key"); image.attr("src","/api/ai-analyses/"+id+"/files/"+fileId);
        }
        return doc.body().html();
    }
    private void afterCommit(Runnable action) { TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){action.run();}}); }
    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST,message); }
}
