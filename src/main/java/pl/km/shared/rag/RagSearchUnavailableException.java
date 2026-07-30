package pl.km.shared.rag;

/**
 * Signals that a {@link RagFacade} search could not be completed. Part of the published
 * contract, because a contract without declared failure modes is incomplete: consumers
 * catch this instead of any RAG-internal exception, so an in-process implementation and a
 * future remote one can report failure identically.
 * <p>
 * {@link #getMessage()} is surfaced verbatim to the caller — the Spring AI MCP server turns
 * an exception thrown by a {@code @Tool} method into a tool result with {@code isError=true}
 * whose text is exactly this message. It must therefore stay free of internal detail
 * (no stack traces, no cause chain, no infrastructure names) and say whether a retry helps.
 */
public class RagSearchUnavailableException extends RuntimeException {

    public RagSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
