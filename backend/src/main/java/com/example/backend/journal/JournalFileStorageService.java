package com.example.backend.journal;

import static com.example.backend.journal.InvestmentJournalDtos.*;

import com.example.backend.trade.ApiException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class JournalFileStorageService {
    private static final Logger log = LoggerFactory.getLogger(JournalFileStorageService.class);
    private final Path root;
    private final long maxFileSize;
    private static final Map<String, byte[]> SIGNATURES = Map.of(
            "jpg", new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff}, "jpeg", new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff},
            "png", new byte[]{(byte)0x89,0x50,0x4e,0x47}, "gif", new byte[]{0x47,0x49,0x46,0x38},
            "webp", new byte[]{0x52,0x49,0x46,0x46});
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png",
            "gif", "image/gif", "webp", "image/webp");

    public JournalFileStorageService(@Value("${app.investment-journal.storage-root:./data/investment-journal}") String root,
            @Value("${app.investment-journal.max-file-size:26214400}") long maxFileSize) {
        this.root = Paths.get(root).toAbsolutePath().normalize(); this.maxFileSize=maxFileSize;
    }

    public StoredFile store(long journalId, MultipartFile file, NewFileManifest manifest) {
        validate(file, manifest);
        String original = safeName(file.getOriginalFilename());
        String extension = extension(original);
        String stored = UUID.randomUUID() + (extension.isEmpty()?"":"."+extension);
        String finalPath = journalId + "/" + stored;
        String stagingPath = ".staging/" + UUID.randomUUID() + ".upload";
        Path target = resolve(stagingPath);
        try { Files.createDirectories(target.getParent()); file.transferTo(target); }
        catch (IOException e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"첨부파일을 저장하지 못했습니다."); }
        String mimeType = "IMAGE".equals(manifest.fileType()) ? IMAGE_TYPES.get(extension) : file.getContentType();
        return new StoredFile(original, stored, finalPath, stagingPath, mimeType, file.getSize(), manifest.fileType(), manifest.sortOrder());
    }

    public void promote(StoredFile file) {
        Path source = resolve(file.stagingPath());
        Path target = resolve(file.filePath());
        try {
            Files.createDirectories(target.getParent());
            try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(source, target); }
        } catch (IOException e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "첨부파일을 저장하지 못했습니다."); }
    }

    public void deleteStored(Collection<StoredFile> files) {
        files.forEach(file -> { delete(file.stagingPath()); delete(file.filePath()); });
    }

    public Path resolve(String relative) {
        Path path=root.resolve(relative).normalize();
        if (!path.startsWith(root)) throw new ApiException(HttpStatus.NOT_FOUND,"첨부파일을 찾을 수 없습니다.");
        return path;
    }
    public void delete(String relative) { try { Files.deleteIfExists(resolve(relative)); } catch (IOException e) { log.warn("investment journal file cleanup failed: relativePath={}", relative); } }
    public void deleteAll(Collection<FileRow> rows) { rows.forEach(f -> { try { Files.deleteIfExists(resolve(f.filePath())); } catch (IOException e) { log.warn("investment journal file cleanup failed: journalId={}, fileId={}, relativePath={}", f.journalId(), f.fileId(), f.filePath()); } }); }

    private void validate(MultipartFile file, NewFileManifest m) {
        if (file==null || file.isEmpty() || file.getOriginalFilename()==null) throw new ApiException(HttpStatus.BAD_REQUEST,"빈 첨부파일은 저장할 수 없습니다.");
        if (file.getSize()>maxFileSize) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"첨부파일 크기가 허용 범위를 초과했습니다.");
        if (m.sortOrder()<0) throw new ApiException(HttpStatus.BAD_REQUEST,"파일 순서는 0 이상이어야 합니다.");
        if (!Set.of("IMAGE","FILE").contains(m.fileType())) throw new ApiException(HttpStatus.BAD_REQUEST,"파일 유형이 올바르지 않습니다.");
        String actual=safeName(file.getOriginalFilename());
        if (!actual.equals(safeName(m.originalFileName()))) throw new ApiException(HttpStatus.BAD_REQUEST,"파일 정보가 업로드 목록과 일치하지 않습니다.");
        if ("IMAGE".equals(m.fileType())) validateImage(file, extension(actual));
    }
    private void validateImage(MultipartFile file, String ext) {
        byte[] signature=SIGNATURES.get(ext.toLowerCase(Locale.ROOT));
        if (signature==null) throw new ApiException(HttpStatus.BAD_REQUEST,"JPEG, PNG, GIF, WebP 이미지만 사용할 수 있습니다.");
        try { byte[] head=file.getInputStream().readNBytes(12); for(int i=0;i<signature.length;i++) if(head.length<=i||head[i]!=signature[i]) throw new ApiException(HttpStatus.BAD_REQUEST,"이미지 파일 형식이 올바르지 않습니다.");
            if ("webp".equalsIgnoreCase(ext) && (head.length<12 || head[8]!='W'||head[9]!='E'||head[10]!='B'||head[11]!='P')) throw new ApiException(HttpStatus.BAD_REQUEST,"이미지 파일 형식이 올바르지 않습니다.");
        } catch(IOException e) { throw new ApiException(HttpStatus.BAD_REQUEST,"이미지 파일을 읽을 수 없습니다."); }
    }
    private String safeName(String name) { String value=name==null?"":Paths.get(name.replace('\\','/')).getFileName().toString().replaceAll("[\\p{Cntrl}]","").trim(); if(value.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"파일명이 필요합니다."); return value.length()>255?value.substring(value.length()-255):value; }
    private String extension(String name) { int i=name.lastIndexOf('.'); return i<0?"":name.substring(i+1).replaceAll("[^A-Za-z0-9]","").toLowerCase(Locale.ROOT); }
}
