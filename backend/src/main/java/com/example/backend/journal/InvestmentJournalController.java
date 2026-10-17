package com.example.backend.journal;

import static com.example.backend.journal.InvestmentJournalDtos.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/investment-journals")
public class InvestmentJournalController {
    private final InvestmentJournalService service; private final JournalFileStorageService storage;
    public InvestmentJournalController(InvestmentJournalService service,JournalFileStorageService storage){this.service=service;this.storage=storage;}
    @GetMapping public JournalPageResponse list(@RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,@RequestParam(required=false) String keyword,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="journalDate") String sortField,@RequestParam(defaultValue="desc") String sortDirection){return service.findAll(new JournalSearchFilter(dateFrom,dateTo,keyword,Math.max(0,page),Math.min(100,Math.max(1,size)),sortField,sortDirection));}
    @GetMapping("/{id}") public JournalDetailResponse detail(@PathVariable long id){return service.findById(id);}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<JournalDetailResponse> create(@RequestPart("request") JournalSaveRequest request,@RequestPart(value="files",required=false) List<MultipartFile> files){var created=service.create(request,files==null?List.of():files);return ResponseEntity.created(URI.create("/api/investment-journals/"+created.journalId())).body(created);}
    @PutMapping(value="/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public JournalDetailResponse update(@PathVariable long id,@RequestPart("request") JournalSaveRequest request,@RequestPart(value="files",required=false) List<MultipartFile> files){return service.update(id,request,files==null?List.of():files);}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable long id){service.delete(id);return ResponseEntity.noContent().build();}
    @GetMapping("/{journalId}/files/{fileId}") public ResponseEntity<FileSystemResource> file(@PathVariable long journalId,@PathVariable long fileId){var f=service.findFile(journalId,fileId);var resource=new FileSystemResource(storage.resolve(f.filePath()));MediaType type;try{type=MediaType.parseMediaType(f.mimeType()==null?MediaType.APPLICATION_OCTET_STREAM_VALUE:f.mimeType());}catch(Exception e){type=MediaType.APPLICATION_OCTET_STREAM;}ContentDisposition disposition=("IMAGE".equals(f.fileType())?ContentDisposition.inline():ContentDisposition.attachment()).filename(f.originalFileName(),StandardCharsets.UTF_8).build();return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString()).header("X-Content-Type-Options","nosniff").body(resource);}
}
