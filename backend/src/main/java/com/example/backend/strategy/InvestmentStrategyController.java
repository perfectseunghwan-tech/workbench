package com.example.backend.strategy;

import static com.example.backend.strategy.InvestmentStrategyDtos.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.example.backend.journal.JournalFileStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/investment-strategies")
public class InvestmentStrategyController {
    private final InvestmentStrategyService service;
    private final JournalFileStorageService storage;
    public InvestmentStrategyController(InvestmentStrategyService service,JournalFileStorageService storage){this.service=service;this.storage=storage;}
    @GetMapping public StrategyPage list(@RequestParam(required=false)String keyword,@RequestParam(required=false)String strategyScope,@RequestParam(required=false)String strategyType,@RequestParam(required=false)String investmentHorizon,@RequestParam(required=false)String status,@RequestParam(defaultValue="latestActivityAt")String sortField,@RequestParam(defaultValue="desc")String sortDirection,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.findAll(new StrategyFilter(keyword,strategyScope,strategyType,investmentHorizon,status,sortField,sortDirection,Math.max(0,page),Math.min(100,Math.max(1,size))));}
    @GetMapping("/links") public List<StrategyLink> links(){return service.links();}
    @GetMapping("/{id}") public StrategyDetail detail(@PathVariable long id){return service.findById(id);}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<StrategyDetail> create(@RequestPart("request") StrategySaveRequest request,@RequestPart(value="files",required=false) List<MultipartFile> files){var result=service.create(request,files==null?List.of():files);return ResponseEntity.created(URI.create("/api/investment-strategies/"+result.strategyId())).body(result);}
    @PutMapping(value="/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public StrategyDetail update(@PathVariable long id,@RequestPart("request") StrategySaveRequest request,@RequestPart(value="files",required=false) List<MultipartFile> files){return service.update(id,request,files==null?List.of():files);}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable long id){service.delete(id);return ResponseEntity.noContent().build();}
    @PostMapping(value="/{id}/posts",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public StrategyPost createPost(@PathVariable long id,@RequestPart("request") PostSaveRequest request,@RequestPart(value="files",required=false) List<MultipartFile> files){return service.createPost(id,request,files==null?List.of():files);}
    @PutMapping(value="/{id}/posts/{postId}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public StrategyPost updatePost(@PathVariable long id,@PathVariable long postId,@RequestPart("request") PostSaveRequest request,@RequestPart(value="files",required=false) List<MultipartFile> files){return service.updatePost(id,postId,request,files==null?List.of():files);}
    @DeleteMapping("/{id}/posts/{postId}") public ResponseEntity<Void> deletePost(@PathVariable long id,@PathVariable long postId){service.deletePost(id,postId);return ResponseEntity.noContent().build();}
    @PostMapping("/{id}/history/{historyId}/restore") public StrategyDetail restore(@PathVariable long id,@PathVariable long historyId){return service.restore(id,historyId);}
    @PutMapping("/{id}/journals") public StrategyDetail replaceJournals(@PathVariable long id,@RequestBody List<Long> journalIds){return service.replaceJournals(id,journalIds);}
    @GetMapping("/{id}/trade-candidates") public List<RelatedTrade> tradeCandidates(@PathVariable long id){return service.findTradeCandidates(id);}
    @PutMapping("/{id}/trades") public StrategyDetail replaceTrades(@PathVariable long id,@RequestBody List<Long> tradeIds){return service.replaceTrades(id,tradeIds);}
    @GetMapping("/{id}/files/{fileId}") public ResponseEntity<FileSystemResource> file(@PathVariable long id,@PathVariable long fileId){return serve(service.findFile(id,fileId));}
    @GetMapping("/{id}/posts/{postId}/files/{fileId}") public ResponseEntity<FileSystemResource> postFile(@PathVariable long id,@PathVariable long postId,@PathVariable long fileId){return serve(service.findPostFile(id,postId,fileId));}
    private ResponseEntity<FileSystemResource> serve(ContentFile f){MediaType type;try{type=MediaType.parseMediaType(f.mimeType()==null?MediaType.APPLICATION_OCTET_STREAM_VALUE:f.mimeType());}catch(Exception e){type=MediaType.APPLICATION_OCTET_STREAM;}ContentDisposition disposition=("IMAGE".equals(f.fileType())?ContentDisposition.inline():ContentDisposition.attachment()).filename(f.originalFileName(),StandardCharsets.UTF_8).build();return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString()).header("X-Content-Type-Options","nosniff").body(new FileSystemResource(storage.resolve(f.filePath())));}
}
