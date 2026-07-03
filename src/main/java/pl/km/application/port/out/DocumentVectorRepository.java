package pl.km.application.port.out;

import pl.km.domain.model.Document;

public interface DocumentVectorRepository {
    void save(Document document, float[] embedding);
}
