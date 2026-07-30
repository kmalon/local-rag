package pl.km.rag.application.model;

import java.util.UUID;

public record Document(UUID id, String name, String content, int chunkIndex) {

    public static Document of(String name, String content) {
        return chunk(name, content, 0);
    }

    public static Document chunk(String name, String content, int chunkIndex) {
        return new Document(UUID.randomUUID(), name, content, chunkIndex);
    }
}
