package pl.km.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.chunking")
public record ChunkingProperties(int chunkSize, double overlapRatio) {

    public ChunkingProperties {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("rag.chunking.chunk-size must be positive");
        }
        if (overlapRatio < 0 || overlapRatio >= 1) {
            throw new IllegalArgumentException("rag.chunking.overlap-ratio must be in [0,1)");
        }
    }
}
