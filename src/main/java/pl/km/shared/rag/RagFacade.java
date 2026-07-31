package pl.km.shared.rag;

import java.util.List;

public interface RagFacade {

    List<RagQueryResult> search(String question, Integer topK, Double minScore);

    RagSearchLimits limits();
}
