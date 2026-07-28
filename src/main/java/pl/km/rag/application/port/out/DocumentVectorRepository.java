package pl.km.rag.application.port.out;

import pl.km.rag.application.model.Document;

public interface DocumentVectorRepository {
    void save(Document document, float[] embedding);
}
