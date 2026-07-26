package pl.km.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "rag.reranker")
public record RerankerProperties(Resource modelUri, Resource tokenizerUri) {

    public RerankerProperties {
        if (modelUri == null) {
            throw new IllegalArgumentException("rag.reranker.model-uri must be set");
        }
        if (tokenizerUri == null) {
            throw new IllegalArgumentException("rag.reranker.tokenizer-uri must be set");
        }
    }
}
