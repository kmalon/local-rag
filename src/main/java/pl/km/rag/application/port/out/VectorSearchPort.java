package pl.km.rag.application.port.out;

import pl.km.rag.application.model.QueryResult;

import java.util.List;

public interface VectorSearchPort {
    List<QueryResult> search(String query, int topK);
}
