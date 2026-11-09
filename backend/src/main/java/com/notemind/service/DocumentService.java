package com.notemind.service;

import com.notemind.config.AppProperties;
import com.notemind.document.DocumentProcessingService;
import com.notemind.document.FileStorage;
import com.notemind.document.PdfTextExtractor;
import com.notemind.document.PdfValidator;
import com.notemind.dto.DocumentDtos.DocumentDto;
import com.notemind.dto.DocumentDtos.DocumentStatusDto;
import com.notemind.dto.DocumentDtos.PageTextDto;
import com.notemind.dto.PageResponse;
import com.notemind.entity.Document;
import com.notemind.entity.Enums.DocumentStatus;
import com.notemind.exception.ApiException;
import com.notemind.repository.DocumentRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final int MAX_NAME_LENGTH = 255;

    private final DocumentRepository documents;
    private final AccessControlService access;
    private final FileStorage storage;
    private final PdfValidator validator;
    private final PdfTextExtractor extractor;
    private final DocumentProcessingService processing;
    private final long maxBytes;

    public DocumentService(DocumentRepository documents, AccessControlService access, FileStorage storage,
                           PdfValidator validator, PdfTextExtractor extractor, DocumentProcessingService processing,
                           AppProperties properties) {
        this.documents = documents;
        this.access = access;
        this.storage = storage;
        this.validator = validator;
        this.extractor = extractor;
        this.processing = processing;
        this.maxBytes = properties.upload().maxBytes();
    }

    @Transactional
    public DocumentDto upload(UUID userId, MultipartFile file, UUID courseId, UUID folderId) {
        if ((courseId == null) == (folderId == null)) {
            throw ApiException.badRequest("Upload to exactly one course or shared folder.");
        }
        if (courseId != null) {
            access.requireCourse(userId, courseId);
        } else {
            access.requireFolderManage(userId, folderId);
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("The uploaded file is empty.");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "The file exceeds the maximum allowed upload size.");
        }

        Path temp = null;
        String key = null;
        try {
            temp = Files.createTempFile("notemind-upload-", ".pdf");
            file.transferTo(temp);
            validator.validate(temp);
            long size = Files.size(temp);
            key = storage.store(temp);
            temp = null;

            Document document = new Document();
            document.setCourseId(courseId);
            document.setFolderId(folderId);
            document.setUserId(userId);
            document.setFilename(sanitizeFilename(file.getOriginalFilename()));
            document.setStorageKey(key);
            document.setFileSize(size);
            document.setStatus(DocumentStatus.UPLOADED);
            Document saved = documents.saveAndFlush(document);

            registerSubmission(saved.getId(), key);
            return DocumentDto.from(saved, true);
        } catch (IOException ex) {
            if (key != null) {
                storage.delete(key);
            }
            throw new UncheckedIOException("Could not store upload", ex);
        } catch (RuntimeException ex) {
            if (key != null) {
                storage.delete(key);
            }
            throw ex;
        } finally {
            deleteQuietly(temp);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentDto> list(UUID userId, UUID courseId, UUID folderId, int page, int size) {
        Specification<Document> spec = DocumentRepository.accessibleBy(userId);
        if (courseId != null) {
            spec = spec.and(DocumentRepository.inCourse(courseId));
        }
        if (folderId != null) {
            spec = spec.and(DocumentRepository.inFolder(folderId));
        }
        Set<UUID> manageable = access.manageableFolders(userId);
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(documents.findAll(spec, pageable),
                document -> DocumentDto.from(document, access.canManage(userId, document, manageable)));
    }

    @Transactional(readOnly = true)
    public DocumentDto get(UUID userId, UUID documentId) {
        Document document = access.requireDocumentRead(userId, documentId);
        return DocumentDto.from(document, access.canManage(userId, document, access.manageableFolders(userId)));
    }

    @Transactional(readOnly = true)
    public DocumentStatusDto status(UUID userId, UUID documentId) {
        Document d = access.requireDocumentRead(userId, documentId);
        return new DocumentStatusDto(d.getId(), d.getStatus(), d.getErrorMessage(), d.getPageCount());
    }

    @Transactional
    public void delete(UUID userId, UUID documentId) {
        Document document = access.requireDocumentManage(userId, documentId);
        documents.delete(document);
        storage.deleteAfterCommit(List.of(document.getStorageKey()));
    }

    public DocumentStatusDto retry(UUID userId, UUID documentId) {
        Document document = access.requireDocumentManage(userId, documentId);
        if (documents.resetFailed(documentId) == 0) {
            throw ApiException.conflict("Only failed documents can be retried.");
        }
        processing.submit(documentId);
        return new DocumentStatusDto(document.getId(), DocumentStatus.UPLOADED, null, document.getPageCount());
    }

    public record PdfFile(InputStream stream, long size) {
    }

    public PdfFile openPdf(UUID userId, UUID documentId) {
        Document document = access.requireDocumentRead(userId, documentId);
        try {
            return new PdfFile(storage.open(document.getStorageKey()), storage.size(document.getStorageKey()));
        } catch (IOException ex) {
            throw ApiException.notFound("Document file");
        }
    }

    public PageTextDto pageText(UUID userId, UUID documentId, int pageNumber) {
        Document document = access.requireDocumentRead(userId, documentId);
        Path temp = null;
        try {
            temp = Files.createTempFile("notemind-page-", ".pdf");
            storage.copyTo(document.getStorageKey(), temp);
            int pages = extractor.countPages(temp);
            if (pageNumber < 1 || pageNumber > pages) {
                throw ApiException.notFound("Page");
            }
            return new PageTextDto(documentId, pageNumber, pages, extractor.extractSinglePage(temp, pageNumber));
        } catch (IOException ex) {
            throw ApiException.notFound("Document file");
        } finally {
            deleteQuietly(temp);
        }
    }

    /** The worker must not start until the row is visible to other transactions. */
    private void registerSubmission(UUID documentId, String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                processing.submit(documentId);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);
                }
            }
        });
    }

    static String sanitizeFilename(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").strip();
        if (name.isEmpty()) {
            name = "document.pdf";
        }
        if (!name.toLowerCase().endsWith(".pdf")) {
            name += ".pdf";
        }
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(name.length() - MAX_NAME_LENGTH);
    }

    private static void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Temp files are also reclaimed by the operating system.
            }
        }
    }
}
