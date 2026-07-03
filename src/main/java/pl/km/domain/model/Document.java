package pl.km.domain.model;

import java.util.UUID;

public record Document(UUID id, String name, String content) {

    public static Document of(String name, String content) {
        return new Document(UUID.randomUUID(), name, content);
    }
}
