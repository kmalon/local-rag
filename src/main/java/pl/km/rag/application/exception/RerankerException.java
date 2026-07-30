package pl.km.rag.application.exception;

/**
 * Thrown when the cross-encoder reranker fails at query time (e.g. inference error).
 * RAG-internal: each inbound adapter decides how to report it (REST maps it to HTTP 503,
 * the facade translates it into its contract exception) rather than a silently wrong result.
 */
public class RerankerException extends RuntimeException {

    public RerankerException(String message, Throwable cause) {
        super(message, cause);
    }
}
