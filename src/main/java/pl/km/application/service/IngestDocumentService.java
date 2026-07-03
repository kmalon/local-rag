package pl.km.application.service;

import org.springframework.stereotype.Service;
import pl.km.application.port.in.IngestDocumentUseCase;
import pl.km.application.port.out.DocumentVectorRepository;
import pl.km.application.port.out.EmbeddingPort;
import pl.km.domain.model.Document;

@Service
public class IngestDocumentService implements IngestDocumentUseCase {

    private final EmbeddingPort embeddingPort;
    private final DocumentVectorRepository documentVectorRepository;

    public IngestDocumentService(EmbeddingPort embeddingPort, DocumentVectorRepository documentVectorRepository) {
        this.embeddingPort = embeddingPort;
        this.documentVectorRepository = documentVectorRepository;
    }

    @Override
    public void ingest(String name, String content) {
        Document document = Document.of(name, content);
        float[] embedding = embeddingPort.embed(content);
        documentVectorRepository.save(document, embedding);
    }
}
