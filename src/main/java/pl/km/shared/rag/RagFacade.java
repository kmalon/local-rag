package pl.km.shared.rag;

import java.util.List;

public interface RagFacade {
    List<RagQueryResult> search(String question, int topK, Double minScore);
}
