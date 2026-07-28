package pl.km.rag.adapter.out.persistence;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import pl.km.rag.application.port.out.VectorSearchPort;
import pl.km.shared.QueryResult;

import java.util.List;

@Component
public class PgVectorSearchAdapter implements VectorSearchPort {

    private final VectorStore vectorStore;

    public PgVectorSearchAdapter(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<QueryResult> search(String query, int topK) {
        // No similarity threshold here: the candidate pool is over-fetched and
        // relevance filtering is delegated to the reranker (see QueryDocumentService).
        return vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .build())
                .stream()
                .map(doc -> new QueryResult(
                        (String) doc.getMetadata().getOrDefault("name", "unknown"),
                        doc.getText(),
                        doc.getScore() != null ? doc.getScore() : 0.0
                ))
                .toList();
    }
}
