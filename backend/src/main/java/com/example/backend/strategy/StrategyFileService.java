package com.example.backend.strategy;

import static com.example.backend.strategy.InvestmentStrategyDtos.*;

import com.example.backend.journal.InvestmentJournalDtos.NewFileManifest;
import com.example.backend.journal.InvestmentJournalDtos.StoredFile;
import com.example.backend.journal.JournalFileStorageService;
import com.example.backend.trade.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StrategyFileService {
    private final InvestmentStrategyRepository repository;
    private final JournalFileStorageService storage;
    private final ObjectMapper mapper;

    public StrategyFileService(InvestmentStrategyRepository repository, JournalFileStorageService storage, ObjectMapper mapper) {
        this.repository=repository; this.storage=storage; this.mapper=mapper;
    }

    public void deleteAfterCommit(List<ContentFile> files) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){files.forEach(f->storage.delete(f.filePath()));}
        });
    }

    public JsonNode save(String ownerColumn,long ownerId,JsonNode content,List<Long> retainedRaw,List<NewFileManifest> newRaw,List<MultipartFile> multipart) {
        List<ContentFile> existing=repository.findFiles(ownerColumn,ownerId);
        List<Long> retained=retainedRaw==null?List.of():retainedRaw;
        List<NewFileManifest> manifests=newRaw==null?List.of():newRaw;
        List<MultipartFile> files=multipart==null?List.of():multipart;
        Set<Long> existingIds=new HashSet<>(); existing.forEach(f->existingIds.add(f.fileId()));
        if(retained.contains(null)||retained.size()!=new HashSet<>(retained).size()||!existingIds.containsAll(retained)) throw bad("유지할 첨부파일이 현재 게시물에 속하지 않습니다.");
        if(manifests.size()!=files.size()) throw bad("파일 목록과 업로드 파일 수가 일치하지 않습니다.");
        if(retained.size()+manifests.size()>30) throw bad("첨부파일은 30개까지 가능합니다.");
        if(files.stream().mapToLong(MultipartFile::getSize).sum()>104857600L) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"첨부파일 전체 크기가 허용 범위를 초과했습니다.");
        Set<String> keys=new HashSet<>();
        for(NewFileManifest m:manifests) if(m==null||m.uploadKey()==null||m.uploadKey().isBlank()||!keys.add(m.uploadKey())) throw bad("신규 파일 식별자가 올바르지 않습니다.");

        List<StoredFile> staged=new ArrayList<>();
        List<ContentFile> created=new ArrayList<>();
        List<ContentFile> removed=existing.stream().filter(f->!retained.contains(f.fileId())).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){removed.forEach(f->storage.delete(f.filePath()));}
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED){created.forEach(f->storage.delete(f.filePath()));storage.deleteStored(staged);}}
        });
        try {
            Map<String,Long> uploaded=new HashMap<>();
            String prefix="strategy_id".equals(ownerColumn)?"strategies/"+ownerId:"strategy-posts/"+ownerId;
            for(int i=0;i<files.size();i++) {
                StoredFile stored=storage.store(prefix,files.get(i),manifests.get(i));
                staged.add(stored);
                long id=repository.insertFile(ownerColumn,ownerId,stored);
                created.add(repository.findFile(ownerColumn,ownerId,id).orElseThrow());
                uploaded.put(manifests.get(i).uploadKey(),id);
            }
            JsonNode normalized=normalize(content,existing,retained,manifests,uploaded);
            repository.updateFileOrder(ownerColumn,ownerId,retained);
            Set<Long> keep=new HashSet<>(retained); created.forEach(f->keep.add(f.fileId()));
            repository.deleteFiles(ownerColumn,ownerId,keep);
            staged.forEach(storage::promote);
            return normalized;
        } catch(RuntimeException e){created.forEach(f->storage.delete(f.filePath()));storage.deleteStored(staged);throw e;}
    }

    private JsonNode normalize(JsonNode content,List<ContentFile> existing,List<Long> retained,List<NewFileManifest> manifests,Map<String,Long> uploaded) {
        Set<Long> images=new HashSet<>(); existing.stream().filter(f->"IMAGE".equals(f.fileType())&&retained.contains(f.fileId())).forEach(f->images.add(f.fileId()));
        Set<String> imageKeys=new HashSet<>(); manifests.stream().filter(f->"IMAGE".equals(f.fileType())).forEach(f->imageKeys.add(f.uploadKey()));
        Set<String> used=new HashSet<>(); JsonNode copy=content.deepCopy();
        visit(copy,node->{
            if(!"journalImage".equals(node.path("type").asText())) return;
            if(!node.isObject()) throw bad("본문 이미지가 올바르지 않습니다.");
            JsonNode raw=node.path("attrs"); String key=raw.path("uploadKey").asText(""); long fileId=raw.path("fileId").asLong(0);
            if(!key.isBlank()) {if(!imageKeys.contains(key)||!used.add(key)) throw bad("본문 이미지와 업로드 파일이 일치하지 않습니다.");fileId=uploaded.get(key);}
            else if(!images.contains(fileId)) throw bad("본문 이미지가 현재 게시물의 파일을 참조하지 않습니다.");
            ObjectNode attrs=mapper.createObjectNode(); attrs.put("fileId",fileId);
            if(raw.hasNonNull("alt")) attrs.put("alt",raw.get("alt").asText());
            if(raw.hasNonNull("title")) attrs.put("title",raw.get("title").asText());
            ((ObjectNode)node).set("attrs",attrs);
        });
        if(!used.equals(imageKeys)) throw bad("업로드한 이미지와 본문 참조가 일치하지 않습니다.");
        return copy;
    }
    private void visit(JsonNode node,java.util.function.Consumer<JsonNode> action){action.accept(node);JsonNode children=node.get("content");if(children!=null&&children.isArray())children.forEach(child->visit(child,action));}
    private ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,message);}
}
