package pl.km.rag;

import pl.km.shared.QueryResult;

import java.util.List;

/**
 * Public entry point of the RAG feature for other features (e.g. the MCP server).
 * Exchanges only shared-kernel types ({@link QueryResult}), so consumers never
 * depend on RAG-internal ports, services or adapters.
 */
public interface RagFacade {

    /**
     * Retrieves the most relevant document chunks for a question.
     *
     * @param question natural-language question
     * @param topK     maximum number of chunks to return
     * @param minScore minimum reranker score, or {@code null} to use the server default
     */
    List<QueryResult> search(String question, int topK, Double minScore);
}
