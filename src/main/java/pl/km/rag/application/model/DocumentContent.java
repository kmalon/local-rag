package pl.km.rag.application.model;

import pl.km.rag.application.exception.InvalidInputException;

public record DocumentContent(String value) {

    public DocumentContent {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("document content must not be blank");
        }
    }
}
