package com.example.retinavision.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "retina.qdrant")
public class QdrantProperties {
    private String baseUrl = "http://127.0.0.1:6333";
    private String apiKey = "";
    private String collection = "retina_knowledge_chunks";
    private double scoreThreshold = 0.2;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }
    public double getScoreThreshold() { return scoreThreshold; }
    public void setScoreThreshold(double scoreThreshold) { this.scoreThreshold = scoreThreshold; }
}
