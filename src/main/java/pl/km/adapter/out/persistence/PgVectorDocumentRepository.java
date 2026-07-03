package pl.km.adapter.out.persistence;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import pl.km.application.port.out.DocumentVectorRepository;

import java.util.List;
import java.util.Map;

@Component
public class PgVectorDocumentRepository implements DocumentVectorRepository {

    private final VectorStore vectorStore;

    public PgVectorDocumentRepository(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void save(pl.km.domain.model.Document document, float[] embedding) {
        Document aiDoc = new Document(
                document.id().toString(),
                document.content(),
                Map.of("name", document.name())
        );
        vectorStore.add(List.of(aiDoc));
    }
}
