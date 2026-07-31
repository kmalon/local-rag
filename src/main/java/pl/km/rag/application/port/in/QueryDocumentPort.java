package pl.km.rag.application.port.in;

import pl.km.rag.application.model.QueryResult;
import pl.km.rag.application.model.SearchLimits;

import java.util.List;

public interface QueryDocumentPort {

    List<QueryResult> query(String question, Integer topK, Double score);

    SearchLimits limits();
}
