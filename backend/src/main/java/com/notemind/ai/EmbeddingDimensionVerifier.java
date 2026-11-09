package com.notemind.ai;

import com.notemind.config.AppProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fails at startup, with a clear message, if the configured dimension and the database column disagree. */
@Configuration
public class EmbeddingDimensionVerifier {

    @Bean
    ApplicationRunner verifyEmbeddingDimension(JdbcTemplate jdbc, AppProperties properties) {
        return args -> {
            Integer column = jdbc.queryForObject("""
                    SELECT atttypmod FROM pg_attribute
                    WHERE attrelid = 'chunks'::regclass AND attname = 'embedding'
                    """, Integer.class);
            int configured = properties.rag().embeddingDimension();
            if (column == null || column != configured) {
                throw new IllegalStateException("NOTEMIND_EMBEDDING_DIMENSION is " + configured
                        + " but chunks.embedding is VECTOR(" + column + "). Use the original dimension,"
                        + " or migrate the column and re-embed existing documents.");
            }
        };
    }
}
