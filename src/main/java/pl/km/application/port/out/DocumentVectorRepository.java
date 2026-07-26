package pl.km.application.port.out;

import pl.km.application.model.Document;

public interface DocumentVectorRepository {
    void save(Document document, float[] embedding);
}
