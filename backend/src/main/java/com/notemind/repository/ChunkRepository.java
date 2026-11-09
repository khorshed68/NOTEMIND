package com.notemind.repository;

import com.notemind.ai.RetrievalScope;
import com.notemind.ai.RetrievedChunk;
import com.notemind.document.TextChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain SQL because pgvector operators and CTE-based rank fusion have no JPA equivalent.
 *
 * <p>Security invariant: every read in this class goes through {@link #AUTHORIZED_DOCUMENTS}, so a
 * chunk the user may not read can never leave the database, whatever the caller passes in.
 */
@Repository
public class ChunkRepository {

    private static final String AUTHORIZED_DOCUMENTS = """
            SELECT d.id, d.filename
            FROM documents d
            WHERE d.status = 'READY'
              AND (d.user_id = :userId
                   OR d.folder_id IN (SELECT f.id FROM class_folders f WHERE f.owner_id = :userId)
                   OR d.folder_id IN (SELECT m.folder_id FROM folder_members m WHERE m.user_id = :userId))
            """;

    private static final int RRF_K = 60;
    /** Natural-language questions favour meaning; keywords mainly rescue exact terms such as acronyms. */
    private static final String KEYWORD_WEIGHT = "0.5";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public ChunkRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    public void insertAll(UUID documentId, List<TextChunk> chunks, List<float[]> vectors) {
        jdbc.batchUpdate("""
                INSERT INTO chunks (id, document_id, page_number, chunk_index, content, embedding)
                VALUES (?, ?, ?, ?, ?, CAST(? AS vector))
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                TextChunk chunk = chunks.get(i);
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, documentId);
                ps.setInt(3, chunk.pageNumber());
                ps.setInt(4, chunk.chunkIndex());
                ps.setString(5, chunk.content());
                ps.setString(6, toVectorLiteral(vectors.get(i)));
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
    }

    public int deleteByDocument(UUID documentId) {
        return jdbc.update("DELETE FROM chunks WHERE document_id = ?", documentId);
    }

    /** Hybrid search: pgvector cosine and PostgreSQL full-text results, merged by reciprocal rank fusion. */
    @Transactional(readOnly = true)
    public List<RetrievedChunk> search(RetrievalScope scope, float[] queryVector, String queryText, int candidates) {
        // If the planner uses HNSW, keep scanning until enough rows survive the tenant filter.
        jdbc.execute("SET LOCAL hnsw.iterative_scan = strict_order");
        jdbc.execute("SET LOCAL hnsw.ef_search = 100");

        MapSqlParameterSource params = scopeParams(scope)
                .addValue("embedding", toVectorLiteral(queryVector))
                .addValue("text", queryText)
                .addValue("candidates", candidates);

        String sql = """
                WITH authorized AS MATERIALIZED (%s),
                vector_hits AS (
                    SELECT c.id, row_number() OVER (ORDER BY c.embedding <=> CAST(:embedding AS vector)) AS rank
                    FROM chunks c JOIN authorized a ON a.id = c.document_id
                    ORDER BY c.embedding <=> CAST(:embedding AS vector)
                    LIMIT :candidates
                ),
                keyword_query AS (
                    SELECT replace(plainto_tsquery('english', :text)::text, '&', '|')::tsquery AS q
                ),
                keyword_hits AS (
                    SELECT c.id, row_number() OVER (ORDER BY ts_rank_cd(c.content_tsv, k.q) DESC) AS rank
                    FROM chunks c
                    JOIN authorized a ON a.id = c.document_id
                    CROSS JOIN keyword_query k
                    WHERE c.content_tsv @@ k.q
                    ORDER BY ts_rank_cd(c.content_tsv, k.q) DESC
                    LIMIT :candidates
                ),
                fused AS (
                    SELECT COALESCE(v.id, k.id) AS id,
                           k.rank IS NOT NULL AS keyword_hit,
                           COALESCE(1.0 / (%d + v.rank), 0) + %s * COALESCE(1.0 / (%d + k.rank), 0) AS score
                    FROM vector_hits v FULL OUTER JOIN keyword_hits k ON v.id = k.id
                )
                SELECT c.id, c.document_id, a.filename, c.page_number, c.chunk_index, c.content,
                       1 - (c.embedding <=> CAST(:embedding AS vector)) AS similarity,
                       f.keyword_hit, f.score
                FROM fused f
                JOIN chunks c ON c.id = f.id
                JOIN authorized a ON a.id = c.document_id
                ORDER BY f.score DESC, c.id
                """.formatted(authorizedSql(scope), RRF_K, KEYWORD_WEIGHT, RRF_K);

        return named.query(sql, params, RETRIEVED_MAPPER);
    }

    public boolean hasSearchableDocuments(RetrievalScope scope) {
        Boolean result = named.queryForObject(
                "SELECT EXISTS (" + authorizedSql(scope) + ")", scopeParams(scope), Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    /** Evenly spaced passages from the scope, used to give quizzes coverage of the whole material. */
    public List<RetrievedChunk> sample(RetrievalScope scope, int limit) {
        String sql = """
                WITH authorized AS MATERIALIZED (%s)
                SELECT c.id, c.document_id, a.filename, c.page_number, c.chunk_index, c.content,
                       0.0 AS similarity, FALSE AS keyword_hit, 0.0 AS score
                FROM chunks c JOIN authorized a ON a.id = c.document_id
                ORDER BY a.filename, c.chunk_index
                """.formatted(authorizedSql(scope));
        List<RetrievedChunk> all = named.query(sql, scopeParams(scope), RETRIEVED_MAPPER);
        if (all.size() <= limit) {
            return all;
        }
        List<RetrievedChunk> sampled = new ArrayList<>(limit);
        double step = all.size() / (double) limit;
        for (int i = 0; i < limit; i++) {
            sampled.add(all.get((int) (i * step)));
        }
        return sampled;
    }

    private static String authorizedSql(RetrievalScope scope) {
        StringBuilder sql = new StringBuilder(AUTHORIZED_DOCUMENTS);
        if (scope.courseId() != null) {
            sql.append(" AND d.course_id = :courseId");
        }
        if (scope.folderId() != null) {
            sql.append(" AND d.folder_id = :folderId");
        }
        if (!scope.documentIds().isEmpty()) {
            sql.append(" AND d.id IN (:documentIds)");
        }
        return sql.toString();
    }

    private static MapSqlParameterSource scopeParams(RetrievalScope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", scope.userId());
        if (scope.courseId() != null) {
            params.addValue("courseId", scope.courseId());
        }
        if (scope.folderId() != null) {
            params.addValue("folderId", scope.folderId());
        }
        if (!scope.documentIds().isEmpty()) {
            params.addValue("documentIds", scope.documentIds());
        }
        return params;
    }

    private static final RowMapper<RetrievedChunk> RETRIEVED_MAPPER = (rs, rowNum) -> new RetrievedChunk(
            rs.getObject("id", UUID.class),
            rs.getObject("document_id", UUID.class),
            rs.getString("filename"),
            rs.getInt("page_number"),
            rs.getInt("chunk_index"),
            rs.getString("content"),
            rs.getDouble("similarity"),
            rs.getBoolean("keyword_hit"),
            rs.getDouble("score"));

    static String toVectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(vector[i]);
        }
        return literal.append(']').toString();
    }
}
