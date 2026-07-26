package pl.km.application.port.out;

import pl.km.application.model.QueryResult;

import java.util.List;

public interface VectorSearchPort {
    List<QueryResult> search(String query, int topK);
}
