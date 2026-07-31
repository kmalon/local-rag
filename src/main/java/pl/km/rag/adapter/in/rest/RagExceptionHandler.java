package pl.km.rag.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.km.rag.application.exception.InvalidInputException;
import pl.km.rag.application.exception.RerankerException;
import pl.km.rag.application.exception.UnsupportedFileTypeException;

@RestControllerAdvice(basePackageClasses = DocumentController.class)
public class RagExceptionHandler {

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(InvalidInputException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFileType(UnsupportedFileTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(RerankerException.class)
    public ResponseEntity<ErrorResponse> handleReranker(RerankerException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(ex.getMessage()));
    }
}
