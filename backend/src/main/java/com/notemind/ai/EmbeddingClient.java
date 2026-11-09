package com.notemind.ai;

import java.util.List;

public interface EmbeddingClient {

    boolean isAvailable();

    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);
}
