package pl.km.rag.application.model;

import pl.km.rag.application.exception.InvalidInputException;

public record DocumentName(String value) {

    public DocumentName {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("document name must not be blank");
        }
    }
}
