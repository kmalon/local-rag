package pl.km.rag.adapter.in;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.km.rag.application.exception.RerankerException;
import pl.km.rag.application.model.QueryResult;
import pl.km.rag.application.port.in.QueryDocumentPort;
import pl.km.shared.rag.RagFacade;
import pl.km.shared.rag.RagQueryResult;
import pl.km.shared.rag.RagSearchUnavailableException;

import java.util.List;

/**
 * Adapts the RAG query use case to the cross-module {@link RagFacade} contract: maps the
 * internal {@link QueryResult} model onto {@link RagQueryResult}, and translates internal
 * failures into {@link RagSearchUnavailableException} so consumers never see RAG internals.
 * Retrieval rules (candidate pool, reranking, default score threshold) stay in the
 * application layer.
 * <p>
 * Failures are logged here with their cause, because the message carried across the boundary
 * is deliberately terse: it is what an MCP client displays verbatim.
 */
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
    public List<RagQueryResult> search(String question, int topK, Double minScore) {
        return query(question, topK, minScore).stream()
                .map(result -> new RagQueryResult(result.name(), result.content(), result.score()))
                .toList();
    }

    /**
     * Fails loudly rather than degrading: an empty or un-reranked result set is
     * indistinguishable from "nothing matched", which serves a caller worse than a visible,
     * retryable error. The question itself is kept out of the logs.
     */
    private List<QueryResult> query(String question, int topK, Double minScore) {
        try {
            return queryDocumentPort.query(question, topK, minScore);
        } catch (RerankerException e) {
            log.error("RAG facade search failed: reranking error (topK={}, minScore={})", topK, minScore, e);
            throw new RagSearchUnavailableException(RERANKING_FAILED, e);
        } catch (RuntimeException e) {
            log.error("RAG facade search failed unexpectedly (topK={}, minScore={})", topK, minScore, e);
            throw new RagSearchUnavailableException(SEARCH_FAILED, e);
        }
    }
}
