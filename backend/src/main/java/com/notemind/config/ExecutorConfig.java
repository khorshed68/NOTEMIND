package com.notemind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfig {

    /** Bounded pool: embedding is CPU/IO heavy, so PDFs queue instead of starving the API. */
    @Bean(name = "documentExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor documentExecutor(AppProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.processing().threads());
        executor.setMaxPoolSize(properties.processing().threads());
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("doc-processing-");
        executor.initialize();
        return executor;
    }

    /** Runs retrieval and LLM streaming so servlet threads return immediately for SSE. */
    @Bean(name = "aiExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-");
        executor.initialize();
        return executor;
    }
}
