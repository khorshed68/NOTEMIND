package com.notemind.controller;

import com.notemind.dto.DocumentDtos.DocumentDto;
import com.notemind.dto.DocumentDtos.DocumentStatusDto;
import com.notemind.dto.DocumentDtos.PageTextDto;
import com.notemind.dto.PageResponse;
import com.notemind.security.AuthenticatedUser;
import com.notemind.service.DocumentService;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto upload(@AuthenticationPrincipal AuthenticatedUser user,
                              @RequestPart("file") MultipartFile file,
                              @RequestParam(required = false) UUID courseId,
                              @RequestParam(required = false) UUID folderId) {
        return documents.upload(user.id(), file, courseId, folderId);
    }

    @GetMapping
    public PageResponse<DocumentDto> list(@AuthenticationPrincipal AuthenticatedUser user,
                                          @RequestParam(required = false) UUID courseId,
                                          @RequestParam(required = false) UUID folderId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        return documents.list(user.id(), courseId, folderId, Math.max(page, 0), Math.max(size, 1));
    }

    @GetMapping("/{id}")
    public DocumentDto get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return documents.get(user.id(), id);
    }

    @GetMapping("/{id}/status")
    public DocumentStatusDto status(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return documents.status(user.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        documents.delete(user.id(), id);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentStatusDto retry(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return documents.retry(user.id(), id);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<InputStreamResource> preview(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @PathVariable UUID id) {
        DocumentService.PdfFile pdf = documents.openPdf(user.id(), id);
        // No Content-Disposition on purpose: PDF.js fetches these bytes with a token, and that header
        // made Edge replace the response with an empty 204 when fetched from script.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.size())
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(pdf.stream()));
    }

    @GetMapping("/{id}/pages/{pageNumber}")
    public PageTextDto page(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                            @PathVariable int pageNumber) {
        return documents.pageText(user.id(), id, pageNumber);
    }
}
