package pl.km.application.service;

import org.springframework.stereotype.Service;
import pl.km.application.port.in.IngestDocumentUseCase;
import pl.km.application.port.in.IngestFileUseCase;
import pl.km.application.port.out.DocumentVectorRepository;
import pl.km.application.port.out.EmbeddingPort;
import pl.km.application.port.out.FileParserPort;
import pl.km.domain.model.Document;

import java.io.InputStream;

@Service
public class IngestDocumentService implements IngestDocumentUseCase, IngestFileUseCase {

    private final EmbeddingPort embeddingPort;
    private final DocumentVectorRepository documentVectorRepository;
    private final FileParserPort fileParserPort;

    public IngestDocumentService(EmbeddingPort embeddingPort, DocumentVectorRepository documentVectorRepository, FileParserPort fileParserPort) {
        this.embeddingPort = embeddingPort;
        this.documentVectorRepository = documentVectorRepository;
        this.fileParserPort = fileParserPort;
    }

    @Override
    public void ingest(String name, String content) {
        Document document = Document.of(name, content);
        float[] embedding = embeddingPort.embed(content);
        documentVectorRepository.save(document, embedding);
    }

    @Override
    public void ingest(String filename, InputStream inputStream) {
        String content = fileParserPort.parse(filename, inputStream);
        ingest(filename, content);
    }
}
