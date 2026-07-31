package pl.km.rag.application;

import pl.km.rag.application.model.*;
import pl.km.rag.application.port.in.QueryDocumentPort;
import pl.km.rag.application.port.out.RerankerPort;
import pl.km.rag.application.port.out.VectorSearchPort;
import pl.km.rag.config.QueryProperties;

import java.util.List;

public class QueryDocumentService implements QueryDocumentPort {

    private final VectorSearchPort vectorSearchPort;
    private final RerankerPort rerankerPort;
    private final QueryProperties queryProperties;

    public QueryDocumentService(VectorSearchPort vectorSearchPort,
                                RerankerPort rerankerPort,
                                QueryProperties queryProperties) {
        this.vectorSearchPort = vectorSearchPort;
        this.rerankerPort = rerankerPort;
        this.queryProperties = queryProperties;
    }

    @Override
    public SearchLimits limits() {
        int maxTopK = queryProperties.maxTopK();
        return new SearchLimits(TopK.boundedBy(null, maxTopK).value(), maxTopK);
    }

    @Override
    public List<QueryResult> query(String question, Integer topK, Double score) {
        Question searchTerm = new Question(question);
        TopK limit = TopK.boundedBy(topK, queryProperties.maxTopK());
        MinScore threshold = new MinScore(score != null ? score : queryProperties.defaultScoreThreshold());

        int poolSize = queryProperties.poolSizeFor(limit.value());
        List<QueryResult> candidates = vectorSearchPort.search(searchTerm.value(), poolSize);
        List<QueryResult> reranked = rerankerPort.rerank(searchTerm.value(), candidates);

        return reranked.stream()
                .filter(r -> r.score() >= threshold.value())
                .limit(limit.value())
                .toList();
    }
}
