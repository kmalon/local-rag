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
        Document aiDoc = Document.builder()
                .id(document.id().toString())
                .text(document.content())
                .metadata(Map.of("name", document.name()))
                .build();
        vectorStore.add(List.of(aiDoc));
    }
}
