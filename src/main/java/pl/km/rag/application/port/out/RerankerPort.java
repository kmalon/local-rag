package pl.km.rag.application.port.out;

import pl.km.rag.application.model.QueryResult;

import java.util.List;

public interface RerankerPort {

    /**
     * Re-scores each candidate against the query using a cross-encoder and
     * returns the candidates sorted by descending relevance score.
     * An empty input yields an empty result.
     */
    List<QueryResult> rerank(String query, List<QueryResult> candidates);
}
