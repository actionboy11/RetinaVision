package com.example.retinavision.rag.knowledge;

public interface EmbeddingClient {
    float[] embed(String text);
}
