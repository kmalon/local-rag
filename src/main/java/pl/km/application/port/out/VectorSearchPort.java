package pl.km.application.port.out;

import pl.km.domain.model.QueryResult;

import java.util.List;

public interface VectorSearchPort {
    List<QueryResult> search(String query, int topK, double scoreThreshold);
}
