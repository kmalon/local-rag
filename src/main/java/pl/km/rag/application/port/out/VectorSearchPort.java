package pl.km.rag.application.port.out;

import pl.km.shared.QueryResult;

import java.util.List;

public interface VectorSearchPort {
    List<QueryResult> search(String query, int topK);
}
