package pl.km.rag.application.port.in;

import pl.km.rag.application.model.QueryResult;

import java.util.List;

public interface QueryDocumentPort {
    List<QueryResult> query(String question, int topK, Double score);
}
