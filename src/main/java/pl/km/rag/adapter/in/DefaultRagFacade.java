package pl.km.rag.adapter.in;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.km.rag.application.exception.InvalidInputException;
import pl.km.rag.application.exception.RerankerException;
import pl.km.rag.application.model.QueryResult;
import pl.km.rag.application.model.SearchLimits;
import pl.km.rag.application.port.in.QueryDocumentPort;
import pl.km.shared.rag.RagFacade;
import pl.km.shared.rag.RagQueryResult;
import pl.km.shared.rag.RagSearchArgumentException;
import pl.km.shared.rag.RagSearchLimits;
import pl.km.shared.rag.RagSearchUnavailableException;

import java.util.List;

public class DefaultRagFacade implements RagFacade {

    private static final Logger log = LoggerFactory.getLogger(DefaultRagFacade.class);

    private static final String RERANKING_FAILED =
            "Document search is temporarily unavailable: relevance reranking failed. Retry in a few seconds.";
    private static final String SEARCH_FAILED =
            "Document search failed unexpectedly. Retry in a few seconds; if it keeps failing "
                    + "the server needs attention.";

    private final QueryDocumentPort queryDocumentPort;

    public DefaultRagFacade(QueryDocumentPort queryDocumentPort) {
        this.queryDocumentPort = queryDocumentPort;
    }

    @Override
    public RagSearchLimits limits() {
        SearchLimits limits = queryDocumentPort.limits();
        return new RagSearchLimits(limits.defaultTopK(), limits.maxTopK());
    }

    @Override
    public List<RagQueryResult> search(String question, Integer topK, Double minScore) {
        return query(question, topK, minScore).stream()
                .map(result -> new RagQueryResult(result.name(), result.content(), result.score()))
                .toList();
    }

    private List<QueryResult> query(String question, Integer topK, Double minScore) {
        try {
            return queryDocumentPort.query(question, topK, minScore);
        } catch (InvalidInputException e) {
            log.debug("RAG facade search rejected: {} (topK={}, minScore={})", e.getMessage(), topK, minScore);
            throw new RagSearchArgumentException(e.getMessage(), e);
        } catch (RerankerException e) {
            log.error("RAG facade search failed: reranking error (topK={}, minScore={})", topK, minScore, e);
            throw new RagSearchUnavailableException(RERANKING_FAILED, e);
        } catch (RuntimeException e) {
            log.error("RAG facade search failed unexpectedly (topK={}, minScore={})", topK, minScore, e);
            throw new RagSearchUnavailableException(SEARCH_FAILED, e);
        }
    }
}
