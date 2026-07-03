package pl.km.application.service;

import org.springframework.stereotype.Service;
import pl.km.application.port.in.QueryDocumentUseCase;
import pl.km.application.port.out.VectorSearchPort;
import pl.km.domain.model.QueryResult;

import java.util.List;

@Service
public class QueryDocumentService implements QueryDocumentUseCase {

    private final VectorSearchPort vectorSearchPort;

    public QueryDocumentService(VectorSearchPort vectorSearchPort) {
        this.vectorSearchPort = vectorSearchPort;
    }

    @Override
    public List<QueryResult> query(String question, int topK) {
        return vectorSearchPort.search(question, topK);
    }
}
