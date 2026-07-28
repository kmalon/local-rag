package pl.km.rag.application;

import pl.km.shared.QueryResult;
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
    public List<QueryResult> query(String question, int topK, Double score) {
        double scoreThreshold = score != null ? score : queryProperties.defaultScoreThreshold();

        int poolSize = Math.max(queryProperties.candidatePoolSize(), topK);
        List<QueryResult> candidates = vectorSearchPort.search(question, poolSize);
        List<QueryResult> reranked = rerankerPort.rerank(question, candidates);

        return reranked.stream()
                .filter(r -> r.score() >= scoreThreshold)
                .limit(topK)
                .toList();
    }
}
