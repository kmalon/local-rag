package pl.km.application.service;

import org.springframework.stereotype.Service;
import pl.km.application.port.in.QueryDocumentUseCase;
import pl.km.application.port.out.RerankerPort;
import pl.km.application.port.out.VectorSearchPort;
import pl.km.config.QueryProperties;
import pl.km.domain.model.QueryResult;

import java.util.List;

@Service
public class QueryDocumentService implements QueryDocumentUseCase {

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

        // Over-fetch a candidate pool by raw vector similarity (no threshold),
        // then let the cross-encoder reranker decide final relevance.
        List<QueryResult> candidates = vectorSearchPort.search(question, queryProperties.candidatePoolSize());
        List<QueryResult> reranked = rerankerPort.rerank(question, candidates);

        return reranked.stream()
                .filter(r -> r.score() >= scoreThreshold)
                .limit(topK)
                .toList();
    }
}
