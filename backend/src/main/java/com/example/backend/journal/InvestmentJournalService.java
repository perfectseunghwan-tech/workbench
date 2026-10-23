package com.example.backend.journal;

import static com.example.backend.journal.InvestmentJournalDtos.*;

import com.example.backend.trade.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InvestmentJournalService {
    private static final Set<String> NODES = Set.of("doc","paragraph","text","heading","bulletList","orderedList","listItem","blockquote","hardBreak","horizontalRule","table","tableRow","tableHeader","tableCell","journalImage");
    private static final Set<String> MARKS = Set.of("bold","italic","strike","code");
    private final InvestmentJournalRepository repository;
    private final JournalFileStorageService storage;
    private final ObjectMapper mapper;
    private final int maxFiles;
    private final long maxRequestSize;
    private final int maxContentBytes;

    public InvestmentJournalService(InvestmentJournalRepository repository, JournalFileStorageService storage,
            ObjectMapper mapper, @Value("${app.investment-journal.max-files:30}") int maxFiles,
            @Value("${app.investment-journal.max-request-size:104857600}") long maxRequestSize,
            @Value("${app.investment-journal.max-content-size:2097152}") int maxContentBytes) {
        this.repository=repository; this.storage=storage; this.mapper=mapper; this.maxFiles=maxFiles;
        this.maxRequestSize=maxRequestSize; this.maxContentBytes=maxContentBytes;
    }

    @Transactional(readOnly=true)
    public JournalPageResponse findAll(JournalSearchFilter filter) {
        if(filter.dateFrom()!=null&&filter.dateTo()!=null&&filter.dateFrom().isAfter(filter.dateTo())) throw bad("조회 시작일은 종료일보다 늦을 수 없습니다.");
        return repository.findAll(filter);
    }
    @Transactional(readOnly=true) public JournalDetailResponse findById(long id) { return detail(row(id)); }
    @Transactional(readOnly=true) public FileRow findFile(long journalId,long fileId) { row(journalId); return repository.findFile(journalId,fileId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"첨부파일을 찾을 수 없습니다.")); }

    @Transactional
    public JournalDetailResponse create(JournalSaveRequest raw,List<MultipartFile> files) {
        JournalSaveRequest request=validate(raw,files,true, List.of());
        long journalId=repository.insert(request, emptyDoc());
        repository.replaceStrategies(journalId, request.relatedStrategyIds());
        List<FileRow> created=new ArrayList<>();
        List<StoredFile> staged=new ArrayList<>();
        deleteAfterRollback(created,staged);
        try {
            Map<String,Long> uploaded=storeFiles(journalId,request,files,created,staged);
            JsonNode normalized=normalizeContent(request.journalContent(),uploaded,Set.of(),imageUploadKeys(request));
            repository.update(journalId,request,normalized);
            staged.forEach(storage::promote);
            return detail(row(journalId));
        } catch(RuntimeException e) { storage.deleteAll(created); storage.deleteStored(staged); throw e; }
    }

    @Transactional
    public JournalDetailResponse update(long id,JournalSaveRequest raw,List<MultipartFile> files) {
        row(id);
        List<FileRow> existing=repository.findFiles(id);
        JournalSaveRequest request=validate(raw,files,false,existing);
        Set<Long> retained=new HashSet<>(request.retainedFileIds());
        Set<Long> retainedImages=new HashSet<>(); existing.stream().filter(f->"IMAGE".equals(f.fileType())&&retained.contains(f.fileId())).forEach(f->retainedImages.add(f.fileId()));
        List<FileRow> removed=existing.stream().filter(f->!retained.contains(f.fileId())).toList();
        List<FileRow> created=new ArrayList<>();
        List<StoredFile> staged=new ArrayList<>();
        deleteAfterRollback(created,staged);
        try {
            Map<String,Long> uploaded=storeFiles(id,request,files,created,staged);
            JsonNode normalized=normalizeContent(request.journalContent(),uploaded,retainedImages,imageUploadKeys(request));
            repository.update(id,request,normalized);
            repository.replaceStrategies(id, request.relatedStrategyIds());
            repository.updateFileOrder(id,request.retainedFileIds());
            repository.deleteFiles(id,retainedWithCreated(retained,created));
            staged.forEach(storage::promote);
            afterCommit(()->storage.deleteAll(removed));
            return detail(row(id));
        } catch(RuntimeException e) { storage.deleteAll(created); storage.deleteStored(staged); throw e; }
    }

    @Transactional
    public void delete(long id) {
        row(id); List<FileRow> files=repository.findFiles(id);
        repository.deleteFiles(id, List.of());
        if(repository.delete(id)==0) throw notFound();
        afterCommit(()->storage.deleteAll(files));
    }

    private JournalSaveRequest validate(JournalSaveRequest r,List<MultipartFile> multipart,boolean create,List<FileRow> existing) {
        if(r==null||r.journalDate()==null) throw bad("일지일자를 입력해 주세요.");
        String title=r.journalTitle()==null?null:r.journalTitle().trim(); if(title!=null&&title.isEmpty()) title=null;
        if(title!=null&&title.length()>200) throw bad("제목은 200자 이하여야 합니다.");
        String format=r.contentFormat()==null||r.contentFormat().isBlank()?"JSON":r.contentFormat().trim().toUpperCase(Locale.ROOT);
        if(!"JSON".equals(format)) throw bad("본문 형식은 JSON이어야 합니다.");
        if(r.journalContent()==null||!r.journalContent().isObject()||!"doc".equals(r.journalContent().path("type").asText())) throw bad("본문은 Tiptap doc 형식이어야 합니다.");
        if(r.journalContent().toString().getBytes(StandardCharsets.UTF_8).length>maxContentBytes) throw bad("일지 본문 크기가 허용 범위를 초과했습니다.");
        List<Long> retained=r.retainedFileIds()==null?List.of():r.retainedFileIds();
        List<NewFileManifest> manifests=r.newFiles()==null?List.of():r.newFiles();
        List<MultipartFile> actual=multipart==null?List.of():multipart;
        if(create&&!retained.isEmpty()) throw bad("신규 일지에는 기존 파일을 지정할 수 없습니다.");
        Set<Long> existingIds=new HashSet<>(); existing.forEach(f->existingIds.add(f.fileId()));
        if(retained.size()!=new HashSet<>(retained).size()||!existingIds.containsAll(retained)) throw bad("유지할 첨부파일이 현재 일지에 속하지 않습니다.");
        if(manifests.size()!=actual.size()) throw bad("파일 목록과 업로드 파일 수가 일치하지 않습니다.");
        if(retained.size()+manifests.size()>maxFiles) throw bad("일지당 첨부파일은 "+maxFiles+"개까지 가능합니다.");
        long total=actual.stream().mapToLong(MultipartFile::getSize).sum(); if(total>maxRequestSize) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"첨부파일 전체 크기가 허용 범위를 초과했습니다.");
        Set<String> keys=new HashSet<>(); for(NewFileManifest m:manifests) { if(m==null||m.uploadKey()==null||m.uploadKey().isBlank()||!keys.add(m.uploadKey())) throw bad("신규 파일 식별자가 올바르지 않습니다."); }
        List<Long> strategies=r.relatedStrategyIds()==null?List.of():r.relatedStrategyIds().stream().filter(Objects::nonNull).distinct().toList();
        if(!repository.strategiesExist(strategies)) throw bad("연결할 수 없는 투자전략이 포함되어 있습니다.");
        return new JournalSaveRequest(r.journalDate(),title,format,r.journalContent(),List.copyOf(retained),List.copyOf(manifests),strategies);
    }

    private Map<String,Long> storeFiles(long journalId,JournalSaveRequest request,List<MultipartFile> files,List<FileRow> created,List<StoredFile> staged) {
        Map<String,Long> ids=new HashMap<>();
        for(int i=0;i<files.size();i++) {
            StoredFile stored=storage.store(journalId,files.get(i),request.newFiles().get(i));
            staged.add(stored);
            long id;
            try { id=repository.insertFile(journalId,stored); }
            catch(RuntimeException e) { storage.deleteStored(List.of(stored)); throw e; }
            FileRow row=repository.findFile(journalId,id).orElseThrow(); created.add(row); ids.put(request.newFiles().get(i).uploadKey(),id);
        }
        return ids;
    }

    private JsonNode normalizeContent(JsonNode input,Map<String,Long> uploaded,Set<Long> retained,Set<String> imageKeys) {
        JsonNode copy=input.deepCopy(); Set<String> usedKeys=new HashSet<>();
        walk(copy,node->{
            if(!node.isObject()) return; String type=node.path("type").asText();
            if(!NODES.contains(type)) throw bad("지원하지 않는 본문 요소가 포함되어 있습니다: "+type);
            JsonNode marks=node.get("marks"); if(marks!=null) for(JsonNode mark:marks) if(!MARKS.contains(mark.path("type").asText())) throw bad("지원하지 않는 텍스트 서식입니다.");
            if("journalImage".equals(type)) {
                ObjectNode attrs=node.has("attrs")&&node.get("attrs").isObject()?(ObjectNode)node.get("attrs"):mapper.createObjectNode();
                long fileId=attrs.path("fileId").asLong(0); String key=attrs.path("uploadKey").asText("");
                if(!key.isBlank()) { Long newId=uploaded.get(key); if(newId==null||!usedKeys.add(key)) throw bad("본문 이미지와 업로드 파일이 일치하지 않습니다."); fileId=newId; }
                else if(fileId<=0||!retained.contains(fileId)) throw bad("본문 이미지가 현재 일지의 유지된 파일을 참조하지 않습니다.");
                ObjectNode clean=mapper.createObjectNode(); clean.put("fileId",fileId); if(attrs.hasNonNull("alt")) clean.put("alt",attrs.get("alt").asText()); if(attrs.hasNonNull("title")) clean.put("title",attrs.get("title").asText()); ((ObjectNode)node).set("attrs",clean);
            }
        });
        if(!usedKeys.equals(imageKeys)) throw bad("업로드한 이미지와 본문 참조가 일치하지 않습니다.");
        return copy;
    }
    private Set<String> imageUploadKeys(JournalSaveRequest request) { Set<String> keys=new HashSet<>(); request.newFiles().stream().filter(f->"IMAGE".equals(f.fileType())).forEach(f->keys.add(f.uploadKey())); return keys; }
    private void walk(JsonNode node,Consumer<JsonNode> action) { action.accept(node); JsonNode children=node.get("content"); if(children!=null&&children.isArray()) children.forEach(child->walk(child,action)); }
    private Set<Long> retainedWithCreated(Set<Long> retained,List<FileRow> created) { Set<Long> result=new HashSet<>(retained); created.forEach(f->result.add(f.fileId())); return result; }
    private JournalRow row(long id) { return repository.findById(id).orElseThrow(this::notFound); }
    private JournalDetailResponse detail(JournalRow r) { List<JournalFileResponse> files=repository.findFiles(r.journalId()).stream().map(f->new JournalFileResponse(f.fileId(),f.fileType(),f.originalFileName(),f.mimeType(),f.fileSize(),f.sortOrder(),f.createdAt(),"/api/investment-journals/"+r.journalId()+"/files/"+f.fileId())).toList(); return new JournalDetailResponse(r.journalId(),r.journalDate(),r.journalTitle(),files.size(),r.createdAt(),r.updatedAt(),r.contentFormat(),r.journalContent(),files,repository.findStrategies(r.journalId())); }
    private JsonNode emptyDoc() { ObjectNode d=mapper.createObjectNode(); d.put("type","doc"); d.putArray("content"); return d; }
    private void afterCommit(Runnable r) { TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){r.run();}}); }
    private void deleteAfterRollback(List<FileRow> files,List<StoredFile> staged) { TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED){storage.deleteAll(files);storage.deleteStored(staged);}}}); }
    private ApiException bad(String m){return new ApiException(HttpStatus.BAD_REQUEST,m);} private ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"투자일지를 찾을 수 없습니다.");}
}
