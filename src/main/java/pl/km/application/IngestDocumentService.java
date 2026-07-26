package pl.km.application;

import pl.km.application.model.Document;
import pl.km.application.port.in.IngestDocumentPort;
import pl.km.application.port.in.IngestFilePort;
import pl.km.application.port.out.DocumentVectorRepository;
import pl.km.application.port.out.EmbeddingPort;
import pl.km.application.port.out.FileParserPort;
import pl.km.application.port.out.TextSplitterPort;

import java.io.InputStream;
import java.util.List;

public class IngestDocumentService implements IngestDocumentPort, IngestFilePort {

    private final EmbeddingPort embeddingPort;
    private final DocumentVectorRepository documentVectorRepository;
    private final FileParserPort fileParserPort;
    private final TextSplitterPort textSplitterPort;

    public IngestDocumentService(EmbeddingPort embeddingPort, DocumentVectorRepository documentVectorRepository,
                                 FileParserPort fileParserPort, TextSplitterPort textSplitterPort) {
        this.embeddingPort = embeddingPort;
        this.documentVectorRepository = documentVectorRepository;
        this.fileParserPort = fileParserPort;
        this.textSplitterPort = textSplitterPort;
    }

    @Override
    public void ingest(String name, String content) {
        List<String> chunks = textSplitterPort.split(content);
        for (int i = 0; i < chunks.size(); i++) {
            Document document = Document.chunk(name, chunks.get(i), i);
            float[] embedding = embeddingPort.embed(document.content());
            documentVectorRepository.save(document, embedding);
        }
    }

    @Override
    public void ingest(String filename, InputStream inputStream) {
        String content = fileParserPort.parse(filename, inputStream);
        ingest(filename, content);
    }
}
