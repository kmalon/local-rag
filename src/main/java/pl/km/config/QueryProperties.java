package pl.km.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.query")
public record QueryProperties(double defaultScoreThreshold) {

    public QueryProperties {
        if (defaultScoreThreshold < 0 || defaultScoreThreshold > 1) {
            throw new IllegalArgumentException("rag.query.default-score-threshold must be in [0,1]");
        }
    }
}
