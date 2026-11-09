package com.notemind.ai;

import com.notemind.config.AppProperties;
import com.notemind.repository.ChunkRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {

    private static final int MAX_CHUNKS_PER_PAGE = 2;

    /**
     * Requests about a whole topic or document ("summarize chapter 2") have no single passage that
     * closely resembles them, so a strict similarity threshold would wrongly report "not found".
     */
    private static final Pattern BROAD_REQUEST = Pattern.compile(
            "\\b(summar(y|ise|ize)|overview|outline|key (points|concepts|topics)|main (points|ideas|topics)"
                    + "|(important|exam) (topics|questions)|recap|mcqs?|quiz)\\b|সারাংশ|সারসংক্ষেপ",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private final ChunkRepository chunks;
    private final EmbeddingClient embeddings;
    private final AppProperties.Rag config;

    public RetrievalService(ChunkRepository chunks, EmbeddingClient embeddings, AppProperties properties) {
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.config = properties.rag();
    }

    public boolean hasSearchableDocuments(RetrievalScope scope) {
        return chunks.hasSearchableDocuments(scope);
    }

    public List<RetrievedChunk> retrieve(RetrievalScope scope, String searchText) {
        boolean broad = isBroad(searchText);
        List<RetrievedChunk> candidates =
                chunks.search(scope, embeddings.embed(searchText), searchText, config.candidates());
        return select(candidates, broad);
    }

    static boolean isBroad(String text) {
        return BROAD_REQUEST.matcher(text).find();
    }

    /** Applies the relevance gate, then keeps the best passages in fused-rank order. */
    List<RetrievedChunk> select(List<RetrievedChunk> rankedCandidates, boolean broad) {
        double threshold = config.similarityThreshold() * (broad ? config.broadThresholdFactor() : 1.0);
        // An exact term match is independent evidence of relevance, so it may pass a slightly lower bar.
        double keywordThreshold = threshold * config.keywordThresholdFactor();
        int limit = broad ? config.broadTopK() : config.topK();

        Map<String, Integer> perPage = new HashMap<>();
        List<RetrievedChunk> selected = new ArrayList<>(limit);
        for (RetrievedChunk chunk : rankedCandidates) {
            boolean relevant = chunk.similarity() >= threshold
                    || (chunk.keywordHit() && chunk.similarity() >= keywordThreshold);
            if (!relevant) {
                continue;
            }
            // Overlapping neighbours add little; two per page leaves room for other sources.
            String pageKey = chunk.documentId() + ":" + chunk.pageNumber();
            if (perPage.merge(pageKey, 1, Integer::sum) > MAX_CHUNKS_PER_PAGE) {
                continue;
            }
            selected.add(chunk);
            if (selected.size() == limit) {
                break;
            }
        }
        return selected;
    }
}
