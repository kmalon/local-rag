package pl.km.application.port.in;

import pl.km.domain.model.QueryResult;

import java.util.List;

public interface QueryDocumentUseCase {
    List<QueryResult> query(String question, int topK);
}
