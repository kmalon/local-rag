package pl.km.rag.adapter.out.persistence;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import pl.km.rag.application.port.out.EmbeddingPort;

@Component
public class SpringAiEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }
}
