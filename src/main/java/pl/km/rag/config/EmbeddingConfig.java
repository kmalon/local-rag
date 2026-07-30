package pl.km.rag.config;

import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    @Bean
    public TransformersEmbeddingModel embeddingModel() {
        return new TransformersEmbeddingModel();
    }
}
