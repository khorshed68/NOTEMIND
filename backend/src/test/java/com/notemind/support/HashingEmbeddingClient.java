package com.notemind.support;

import com.notemind.ai.EmbeddingClient;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic test double: hashed bag of words. Texts sharing words get similar vectors,
 * so the real SQL search can be exercised without a model or network access.
 */
public class HashingEmbeddingClient implements EmbeddingClient {

    private final int dimension;

    public HashingEmbeddingClient(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        boolean any = false;
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() > 2) {
                vector[Math.floorMod(token.hashCode(), dimension)] += 1f;
                any = true;
            }
        }
        if (!any) {
            vector[0] = 1f;
        }
        return vector;
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
