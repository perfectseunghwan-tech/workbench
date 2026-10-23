package com.example.backend.analysis;

import static com.example.backend.analysis.AiAnalysisDtos.*;

import com.example.backend.journal.JournalFileStorageService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai-analyses")
public class AiAnalysisController {
    private final AiAnalysisService service;
    private final JournalFileStorageService storage;
    public AiAnalysisController(AiAnalysisService service,JournalFileStorageService storage) { this.service=service; this.storage=storage; }
    @GetMapping public Page list(@RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) String type,@RequestParam(required=false) String purpose,
            @RequestParam(required=false) String source,@RequestParam(required=false) String target,@RequestParam(required=false) String tag,
            @RequestParam(required=false) String review,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.search(dateFrom==null?null:dateFrom.atStartOfDay(),dateTo==null?null:dateTo.atStartOfDay(),keyword,type,purpose,source,target,tag,review,page,size);
    }
    @GetMapping("/{id}") public Detail detail(@PathVariable long id) { return service.find(id); }
    @GetMapping("/related-options") public List<RelatedOption> relatedOptions(@RequestParam String type,@RequestParam(required=false) String keyword) { return service.relatedOptions(type,keyword); }
    @GetMapping("/stock-options") public List<RelatedOption> stockOptions(@RequestParam(required=false) String keyword) { return service.stockOptions(keyword); }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<Detail> create(@RequestPart("request") SaveRequest request,@RequestPart(value="files",required=false) List<MultipartFile> files) {
        Detail result=service.create(request,files==null?List.of():files); return ResponseEntity.created(URI.create("/api/ai-analyses/"+result.analysisId())).body(result);
    }
    @PutMapping(value="/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public Detail update(@PathVariable long id,@RequestPart("request") SaveRequest request,@RequestPart(value="files",required=false) List<MultipartFile> files) { return service.update(id,request,files==null?List.of():files); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable long id) { service.delete(id); return ResponseEntity.noContent().build(); }
    @GetMapping("/{id}/files/{fileId}") public ResponseEntity<FileSystemResource> file(@PathVariable long id,@PathVariable long fileId) {
        FileInfo f=service.file(id,fileId);
        MediaType type; try { type=MediaType.parseMediaType(f.mimeType()==null?MediaType.APPLICATION_OCTET_STREAM_VALUE:f.mimeType()); } catch(Exception e) { type=MediaType.APPLICATION_OCTET_STREAM; }
        ContentDisposition disposition=("IMAGE".equals(f.fileType())?ContentDisposition.inline():ContentDisposition.attachment()).filename(f.originalFileName(),StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString()).header("X-Content-Type-Options","nosniff").body(new FileSystemResource(storage.resolve(f.filePath())));
    }
}
