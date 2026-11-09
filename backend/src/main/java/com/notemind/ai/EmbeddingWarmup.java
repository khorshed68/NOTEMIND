package com.notemind.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The local model's native engine loads on first use, which can mean a large one-time download.
 * Doing it here keeps the app "not ready" until then, so no student's first question pays for it.
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "transformers")
public class EmbeddingWarmup {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingWarmup.class);

    @Bean
    ApplicationRunner warmUpLocalEmbeddings(EmbeddingClient embeddings) {
        return args -> {
            try {
                embeddings.embed("warm up");
                log.info("Local embedding model is ready");
            } catch (RuntimeException ex) {
                log.warn("Local embedding model failed to warm up: {}", ex.getMessage());
            }
        };
    }
}
