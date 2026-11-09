package com.notemind.ai;

import com.notemind.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class SpringAiEmbeddingClient implements EmbeddingClient {

    private final ObjectProvider<EmbeddingModel> embeddingModel;
    private final int batchSize;
    private final int dimension;

    public SpringAiEmbeddingClient(ObjectProvider<EmbeddingModel> embeddingModel, AppProperties properties) {
        this.embeddingModel = embeddingModel;
        this.batchSize = properties.rag().embeddingBatchSize();
        this.dimension = properties.rag().embeddingDimension();
    }

    @Override
    public boolean isAvailable() {
        return embeddingModel.getIfAvailable() != null;
    }

    @Override
    public float[] embed(String text) {
        return checked(model().embed(text));
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        EmbeddingModel model = model();
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + batchSize));
            model.embed(batch).forEach(vector -> vectors.add(checked(vector)));
        }
        return vectors;
    }

    private EmbeddingModel model() {
        EmbeddingModel model = embeddingModel.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("No embedding model is configured");
        }
        return model;
    }

    private float[] checked(float[] vector) {
        if (vector.length != dimension) {
            throw new IllegalStateException("Embedding model returned " + vector.length
                    + " dimensions but NOTEMIND_EMBEDDING_DIMENSION is " + dimension);
        }
        return vector;
    }
}
