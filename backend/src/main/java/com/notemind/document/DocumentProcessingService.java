package com.notemind.document;

import com.notemind.ai.EmbeddingClient;
import com.notemind.entity.Document;
import com.notemind.entity.Enums.DocumentStatus;
import com.notemind.repository.ChunkRepository;
import com.notemind.repository.DocumentRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Background pipeline: extract, clean, chunk, embed, store. Network and CPU work happen outside
 * any transaction; only the final swap of chunks plus the status change is transactional.
 */
@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);
    private static final String GENERIC_FAILURE = "Processing failed unexpectedly. Please retry.";

    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final FileStorage storage;
    private final PdfTextExtractor extractor;
    private final HeaderFooterCleaner cleaner;
    private final TextChunker chunker;
    private final EmbeddingClient embeddings;
    private final TaskExecutor executor;
    private final TransactionTemplate transaction;

    public DocumentProcessingService(DocumentRepository documents, ChunkRepository chunks, FileStorage storage,
                                     PdfTextExtractor extractor, HeaderFooterCleaner cleaner, TextChunker chunker,
                                     EmbeddingClient embeddings, @Qualifier("documentExecutor") TaskExecutor executor,
                                     PlatformTransactionManager transactionManager) {
        this.documents = documents;
        this.chunks = chunks;
        this.storage = storage;
        this.extractor = extractor;
        this.cleaner = cleaner;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.executor = executor;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public void submit(UUID documentId) {
        executor.execute(() -> process(documentId));
    }

    /** Picks up work interrupted by a restart. Assumes a single backend instance, as in compose.yaml. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterrupted() {
        int requeued = documents.requeueInterrupted();
        List<Document> pending = documents.findByStatusIn(List.of(DocumentStatus.UPLOADED));
        if (!pending.isEmpty()) {
            log.info("Resuming {} documents ({} were mid-processing)", pending.size(), requeued);
            pending.forEach(document -> submit(document.getId()));
        }
    }

    void process(UUID documentId) {
        if (documents.claimForProcessing(documentId) == 0) {
            log.debug("Document {} already claimed or deleted", documentId);
            return;
        }
        Path temp = null;
        try {
            Document document = documents.findById(documentId).orElseThrow();
            temp = Files.createTempFile("notemind-", ".pdf");
            storage.copyTo(document.getStorageKey(), temp);

            List<PageText> pages = cleaner.clean(extractor.extract(temp));
            List<TextChunk> textChunks = chunker.chunk(pages);
            if (textChunks.isEmpty()) {
                throw new DocumentProcessingException("No usable text was found in this PDF.");
            }
            if (!embeddings.isAvailable()) {
                throw new DocumentProcessingException("No embedding model is configured on this server.");
            }
            List<float[]> vectors = embeddings.embedAll(textChunks.stream()
                    .map(chunk -> embeddingInput(document.getFilename(), chunk))
                    .toList());

            transaction.executeWithoutResult(status -> {
                // Replacing inside one transaction makes a retry safe and never exposes partial results.
                chunks.deleteByDocument(documentId);
                chunks.insertAll(documentId, textChunks, vectors);
                documents.markReady(documentId, pages.size());
            });
            log.info("Document {} ready: {} pages, {} chunks", documentId, pages.size(), textChunks.size());
        } catch (DocumentProcessingException ex) {
            log.info("Document {} rejected: {}", documentId, ex.getMessage());
            documents.markFailed(documentId, ex.getMessage());
        } catch (Exception ex) {
            log.error("Document {} failed", documentId, ex);
            documents.markFailed(documentId, describe(ex));
        } finally {
            deleteQuietly(temp);
        }
    }

    /** The title goes into the vector only, so course-wide questions can match on what a file is about. */
    public static String embeddingInput(String filename, TextChunk chunk) {
        String title = filename.replaceAll("(?i)\\.pdf$", "").replaceAll("[_-]+", " ");
        return title + "\n\n" + chunk.content();
    }

    private static String describe(Exception ex) {
        String text = String.valueOf(ex.getMessage()).toLowerCase();
        if (text.contains("embedding") || text.contains("api key") || text.contains("401")
                || text.contains("connection refused") || text.contains("429")) {
            return "The embedding service could not process this document. Please retry shortly.";
        }
        return GENERIC_FAILURE;
    }

    private static void deleteQuietly(Path temp) {
        if (temp != null) {
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ex) {
                log.debug("Could not delete temp file {}", temp, ex);
            }
        }
    }
}
