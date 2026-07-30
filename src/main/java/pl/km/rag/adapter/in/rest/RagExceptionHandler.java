package pl.km.rag.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.km.rag.application.exception.RerankerException;
import pl.km.rag.application.exception.UnsupportedFileTypeException;

/**
 * Maps RAG failures onto HTTP for this module's REST adapter only — error mapping is
 * protocol-specific, so it belongs to the adapter that owns the protocol. The MCP adapter
 * reports the same failures through {@code RagFacade}'s contract exception instead, which
 * the MCP server renders as an {@code isError} tool result rather than an HTTP status.
 */
@RestControllerAdvice(basePackageClasses = DocumentController.class)
public class RagExceptionHandler {

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFileType(UnsupportedFileTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(RerankerException.class)
    public ResponseEntity<ErrorResponse> handleReranker(RerankerException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(ex.getMessage()));
    }
}
