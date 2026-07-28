package pl.km.rag;

import org.springframework.stereotype.Component;
import pl.km.rag.application.port.in.QueryDocumentPort;
import pl.km.shared.QueryResult;

import java.util.List;

/**
 * Thin delegation to the RAG use case; retrieval rules (candidate pool, reranking,
 * default score threshold) stay in the application layer.
 */
@Component
class DefaultRagFacade implements RagFacade {

    private final QueryDocumentPort queryDocumentPort;

    DefaultRagFacade(QueryDocumentPort queryDocumentPort) {
        this.queryDocumentPort = queryDocumentPort;
    }

    @Override
    public List<QueryResult> search(String question, int topK, Double minScore) {
        return queryDocumentPort.query(question, topK, minScore);
    }
}
