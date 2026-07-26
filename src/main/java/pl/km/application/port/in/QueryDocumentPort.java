package pl.km.application.port.in;

import pl.km.application.model.QueryResult;

import java.util.List;

public interface QueryDocumentPort {
    List<QueryResult> query(String question, int topK, Double score);
}
