package pl.km.application.exception;

/**
 * Thrown when the cross-encoder reranker fails at query time (e.g. inference error).
 * Mapped to HTTP 503 so callers see a clear "reranker unavailable" signal rather than
 * a silently wrong result.
 */
public class RerankerException extends RuntimeException {

    public RerankerException(String message, Throwable cause) {
        super(message, cause);
    }
}
