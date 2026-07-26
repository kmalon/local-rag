package pl.km.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.km.application.IngestDocumentService;
import pl.km.application.QueryDocumentService;
import pl.km.application.port.out.DocumentVectorRepository;
import pl.km.application.port.out.EmbeddingPort;
import pl.km.application.port.out.FileParserPort;
import pl.km.application.port.out.RerankerPort;
import pl.km.application.port.out.TextSplitterPort;
import pl.km.application.port.out.VectorSearchPort;

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
