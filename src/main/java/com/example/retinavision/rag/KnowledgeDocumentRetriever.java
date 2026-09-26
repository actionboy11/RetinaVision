package com.example.retinavision.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;

public interface KnowledgeDocumentRetriever extends DocumentRetriever {
    List<Document> retrieve(String collection, Query query, int topK);
}
