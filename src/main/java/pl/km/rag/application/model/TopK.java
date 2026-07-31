package pl.km.rag.application.model;

import pl.km.rag.application.exception.InvalidInputException;

public record TopK(int value) {

    public static final int DEFAULT = 5;

    public TopK {
        if (value < 1) {
            throw new InvalidInputException("topK must be at least 1");
        }
    }

    public static TopK boundedBy(Integer requested, int maxResults) {
        int value = requested == null ? Math.min(DEFAULT, maxResults) : requested;
        if (value > maxResults) {
            throw new InvalidInputException("topK must be between 1 and " + maxResults);
        }
        return new TopK(value);
    }
}
