package pl.km.rag.application.model;

import pl.km.rag.application.exception.InvalidInputException;

public record Question(String value) {

    public Question {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("question must not be blank");
        }
    }
}
