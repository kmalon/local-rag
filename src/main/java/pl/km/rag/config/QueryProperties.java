package pl.km.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.query")
public record QueryProperties(double defaultScoreThreshold,
                              int overFetchFactor,
                              int minCandidates,
                              int maxCandidates) {

    public QueryProperties {
        if (defaultScoreThreshold < 0 || defaultScoreThreshold > 1) {
            throw new IllegalArgumentException("rag.query.default-score-threshold must be in [0,1]");
        }
        if (overFetchFactor < 1) {
            throw new IllegalArgumentException("rag.query.over-fetch-factor must be >= 1");
        }
        if (minCandidates < 1) {
            throw new IllegalArgumentException("rag.query.min-candidates must be > 0");
        }
        if (maxCandidates < minCandidates) {
            throw new IllegalArgumentException("rag.query.max-candidates must be >= rag.query.min-candidates");
        }
        if (maxCandidates < overFetchFactor) {
            throw new IllegalArgumentException("rag.query.max-candidates must be >= rag.query.over-fetch-factor");
        }
    }

    public int maxTopK() {
        return maxCandidates / overFetchFactor;
    }

    public int poolSizeFor(int topK) {
        return Math.min(maxCandidates, Math.max(minCandidates, topK * overFetchFactor));
    }
}
