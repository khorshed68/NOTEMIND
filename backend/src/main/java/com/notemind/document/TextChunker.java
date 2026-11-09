package com.notemind.document;

import com.notemind.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Page-aware recursive splitter. Text is divided at the most natural boundary available
 * (paragraph, sentence, line, word), then merged back up to the target size with overlap.
 * Chunking one page at a time is what guarantees exact page citations.
 */
@Component
public class TextChunker {

    private static final String[] SEPARATORS = {"\\n\\s*\\n", "(?<=[.!?\\u0964])\\s+", "\\n", " "};
    private static final int MIN_CHUNK_CHARS = 20;

    private final int chunkSize;
    private final int overlap;

    @Autowired
    public TextChunker(AppProperties properties) {
        this(properties.rag().chunkSize(), properties.rag().chunkOverlap());
    }

    TextChunker(int chunkSize, int overlap) {
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("Chunk overlap must be smaller than chunk size");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<TextChunk> chunk(List<PageText> pages) {
        List<TextChunk> chunks = new ArrayList<>();
        int index = 0;
        for (PageText page : pages) {
            for (String content : splitPage(page.text())) {
                chunks.add(new TextChunk(page.pageNumber(), index++, content));
            }
        }
        return chunks;
    }

    private List<String> splitPage(String text) {
        String stripped = text.strip();
        if (stripped.isEmpty()) {
            return List.of();
        }
        if (stripped.length() <= chunkSize) {
            return stripped.length() >= MIN_CHUNK_CHARS ? List.of(stripped) : List.of();
        }
        List<String> pieces = new ArrayList<>();
        atomize(stripped, 0, pieces);
        return merge(pieces);
    }

    private void atomize(String text, int level, List<String> out) {
        if (text.length() <= chunkSize) {
            if (!text.isBlank()) {
                out.add(text.strip());
            }
            return;
        }
        if (level >= SEPARATORS.length) {
            for (int start = 0; start < text.length(); start += chunkSize) {
                out.add(text.substring(start, Math.min(text.length(), start + chunkSize)));
            }
            return;
        }
        for (String part : text.split(SEPARATORS[level])) {
            atomize(part, level + 1, out);
        }
    }

    private List<String> merge(List<String> pieces) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (current.length() > 0 && current.length() + 1 + piece.length() > chunkSize) {
                String finished = current.toString().strip();
                addIfUseful(chunks, finished);
                current = new StringBuilder(overlapTail(finished, chunkSize - piece.length() - 1));
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(piece);
        }
        addIfUseful(chunks, current.toString().strip());
        return chunks;
    }

    private void addIfUseful(List<String> chunks, String chunk) {
        if (chunk.length() >= MIN_CHUNK_CHARS) {
            chunks.add(chunk);
        }
    }

    /** Trailing text of the finished chunk, starting on a word boundary and limited to the space available. */
    private String overlapTail(String text, int available) {
        int length = Math.min(overlap, Math.max(0, available));
        if (length <= 0 || text.length() <= length) {
            return "";
        }
        int start = text.length() - length;
        int boundary = -1;
        for (int i = start; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                boundary = i;
                break;
            }
        }
        return boundary < 0 ? "" : text.substring(boundary).strip();
    }
}
