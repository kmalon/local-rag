package pl.km.application.service;

import org.springframework.stereotype.Service;
import pl.km.application.port.in.QueryDocumentUseCase;
import pl.km.application.port.out.VectorSearchPort;
import pl.km.config.QueryProperties;
import pl.km.domain.model.QueryResult;

import java.util.List;

@Service
public class QueryDocumentService implements QueryDocumentUseCase {

    private final VectorSearchPort vectorSearchPort;
    private final QueryProperties queryProperties;

    public QueryDocumentService(VectorSearchPort vectorSearchPort, QueryProperties queryProperties) {
        this.vectorSearchPort = vectorSearchPort;
        this.queryProperties = queryProperties;
    }

    @Override
    public List<QueryResult> query(String question, int topK, Double score) {
        double scoreThreshold = score != null ? score : queryProperties.defaultScoreThreshold();
        return vectorSearchPort.search(question, topK, scoreThreshold);
    }
}
