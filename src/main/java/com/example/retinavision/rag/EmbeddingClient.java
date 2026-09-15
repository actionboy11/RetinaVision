package com.example.retinavision.rag;

public interface EmbeddingClient {
    float[] embed(String text);
}
