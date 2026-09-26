package com.example.retinavision.rag;

import com.example.retinavision.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiEmbeddingClientTest {

    @Test
    void delegatesToSpringAiEmbeddingModel() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        EmbeddingProperties properties = properties(2);
        when(model.embed("question")).thenReturn(new float[]{0.1f, 0.2f});

        assertThat(new SpringAiEmbeddingClient(model, properties).embed("question"))
                .containsExactly(0.1f, 0.2f);
    }

    @Test
    void rejectsUnexpectedEmbeddingDimension() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        EmbeddingProperties properties = properties(1024);
        when(model.embed("question")).thenReturn(new float[]{0.1f});

        assertThatThrownBy(() -> new SpringAiEmbeddingClient(model, properties).embed("question"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("维度");
    }

    private EmbeddingProperties properties(int dimension) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEnabled(true);
        properties.setDimension(dimension);
        return properties;
    }
}
