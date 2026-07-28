package pl.km.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.km.rag.application.IngestDocumentService;
import pl.km.rag.application.QueryDocumentService;
import pl.km.rag.application.port.out.DocumentVectorRepository;
import pl.km.rag.application.port.out.EmbeddingPort;
import pl.km.rag.application.port.out.FileParserPort;
import pl.km.rag.application.port.out.RerankerPort;
import pl.km.rag.application.port.out.TextSplitterPort;
import pl.km.rag.application.port.out.VectorSearchPort;

@Configuration
public class ApplicationConfig {

    @Bean
    public IngestDocumentService ingestDocumentService(EmbeddingPort embeddingPort,
                                                       DocumentVectorRepository documentVectorRepository,
                                                       FileParserPort fileParserPort,
                                                       TextSplitterPort textSplitterPort) {
        return new IngestDocumentService(embeddingPort, documentVectorRepository, fileParserPort, textSplitterPort);
    }

    @Bean
    public QueryDocumentService queryDocumentService(VectorSearchPort vectorSearchPort,
                                                     RerankerPort rerankerPort,
                                                     QueryProperties queryProperties) {
        return new QueryDocumentService(vectorSearchPort, rerankerPort, queryProperties);
    }
}
