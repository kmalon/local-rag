package pl.km.rag.application.model;

import pl.km.rag.application.exception.InvalidInputException;

public record MinScore(double value) {

    public MinScore {
        if (value < 0 || value > 1) {
            throw new InvalidInputException("minScore must be between 0 and 1");
        }
    }
}
