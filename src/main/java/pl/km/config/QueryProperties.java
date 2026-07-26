package pl.km.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.query")
public record QueryProperties(double defaultScoreThreshold, int candidatePoolSize) {

    public QueryProperties {
        if (defaultScoreThreshold < 0 || defaultScoreThreshold > 1) {
            throw new IllegalArgumentException("rag.query.default-score-threshold must be in [0,1]");
        }
        if (candidatePoolSize <= 0) {
            throw new IllegalArgumentException("rag.query.candidate-pool-size must be > 0");
        }
    }
}
