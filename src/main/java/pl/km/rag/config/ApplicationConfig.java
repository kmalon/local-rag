package pl.km.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.km.rag.adapter.in.DefaultRagFacade;
import pl.km.rag.application.IngestDocumentService;
import pl.km.rag.application.QueryDocumentService;
import pl.km.rag.application.port.out.*;
import pl.km.shared.rag.RagFacade;

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

    @Bean
    public RagFacade ragFacade(QueryDocumentService queryDocumentService) {
        return new DefaultRagFacade(queryDocumentService);
    }

}
