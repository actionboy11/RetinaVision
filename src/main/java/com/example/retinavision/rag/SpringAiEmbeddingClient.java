package com.example.retinavision.rag;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "retina.ai", name = "framework", havingValue = "spring-ai")
public class SpringAiEmbeddingClient implements EmbeddingClient {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties properties;

    public SpringAiEmbeddingClient(EmbeddingModel embeddingModel, EmbeddingProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public float[] embed(String text) {
        if (!properties.isEnabled()) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Embedding service is disabled");
        }
        try {
            float[] vector = embeddingModel.embed(text);
            if (vector == null || vector.length == 0) {
                throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Embedding 服务返回了空向量");
            }
            if (properties.getDimension() > 0 && vector.length != properties.getDimension()) {
                throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Embedding 向量维度与配置不一致");
            }
            return vector;
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Embedding service call failed");
        }
    }
}
