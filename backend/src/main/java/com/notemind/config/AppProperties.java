package com.notemind.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notemind")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Storage storage,
        @Valid @NotNull Upload upload,
        @Valid @NotNull Processing processing,
        @Valid @NotNull Rag rag,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Ocr ocr,
        @NotNull Admin admin) {

    public record Jwt(@NotBlank String secret, @NotNull Duration accessTtl, @NotNull Duration refreshTtl,
                      boolean cookieSecure) {
    }

    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    public record Storage(@NotBlank String rootDir) {
    }

    public record Upload(@Min(1) long maxBytes, @Min(1) int maxPages) {
    }

    public record Processing(@Min(1) int threads) {
    }

    public record Rag(
            @Min(1) int embeddingDimension,
            @Min(1) int embeddingBatchSize,
            @Min(100) int chunkSize,
            @Min(0) int chunkOverlap,
            @Min(1) int topK,
            @Min(1) int candidates,
            @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold,
            @DecimalMin("0.0") @DecimalMax("1.0") double keywordThresholdFactor,
            @DecimalMin("0.0") @DecimalMax("1.0") double broadThresholdFactor,
            @Min(1) int broadTopK,
            @Min(0) int historyMessages) {
    }

    public record RateLimit(@Min(1) int aiPerMinute, @Min(1) int uploadsPerHour, @Min(1) int authPerMinute) {
    }

    public record Ocr(boolean enabled, @NotBlank String command, @NotBlank String languages,
                      @Min(72) int dpi, @Min(0) int minPageChars) {
    }

    public record Admin(String email, String password, String name) {
    }
}
