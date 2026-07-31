package pl.km.rag.adapter.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.km.config.SecurityConfig;
import pl.km.rag.application.IngestDocumentService;
import pl.km.rag.application.QueryDocumentService;
import pl.km.rag.application.port.out.*;
import pl.km.rag.config.QueryProperties;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import({SecurityConfig.class, DocumentControllerValidationTest.RealServices.class})
@TestPropertySource(properties = {
        "keycloak.issuer-uri=http://localhost:8081/realms/local-rag",
        "keycloak.jwk-set-uri=http://localhost:8081/realms/local-rag/protocol/openid-connect/certs",
        "keycloak.audience.api=rag-platform",
        "keycloak.audience.mcp=rag-mcp",
        "mcp.resource=http://localhost:8080/mcp"
})
class DocumentControllerValidationTest {

    private static final int OVER_FETCH_FACTOR = 4;
    private static final int MIN_CANDIDATES = 20;
    private static final int MAX_CANDIDATES = 80;
    private static final int MAX_TOP_K = MAX_CANDIDATES / OVER_FETCH_FACTOR;

    @TestConfiguration
    static class RealServices {

        @Bean
        QueryDocumentService queryDocumentService(VectorSearchPort vectorSearchPort, RerankerPort rerankerPort) {
            return new QueryDocumentService(vectorSearchPort, rerankerPort,
                    new QueryProperties(0.75, OVER_FETCH_FACTOR, MIN_CANDIDATES, MAX_CANDIDATES));
        }

        @Bean
        IngestDocumentService ingestDocumentService(EmbeddingPort embeddingPort,
                                                    DocumentVectorRepository documentVectorRepository,
                                                    FileParserPort fileParserPort,
                                                    TextSplitterPort textSplitterPort) {
            return new IngestDocumentService(embeddingPort, documentVectorRepository, fileParserPort, textSplitterPort);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VectorSearchPort vectorSearchPort;
    @MockitoBean
    private RerankerPort rerankerPort;
    @MockitoBean
    private EmbeddingPort embeddingPort;
    @MockitoBean
    private DocumentVectorRepository documentVectorRepository;
    @MockitoBean
    private FileParserPort fileParserPort;
    @MockitoBean
    private TextSplitterPort textSplitterPort;

    private MockHttpServletRequestBuilder query(String body) {
        return post("/api/documents/query")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_rag_user")))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder ingest(String body) {
        return post("/api/documents/ingest")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_rag_admin")))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void rejectsTopKBelowOne() throws Exception {
        mockMvc.perform(query("{\"question\":\"hi\",\"topK\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("topK must be at least 1"));
    }

    @Test
    void rejectsTopKBeyondWhatTheServerServes() throws Exception {
        mockMvc.perform(query("{\"question\":\"hi\",\"topK\":100000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("topK must be between 1 and " + MAX_TOP_K));
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        mockMvc.perform(query("{\"question\":\"   \",\"topK\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("question must not be blank"));
    }

    @Test
    void rejectsScoreOutsideTheUnitInterval() throws Exception {
        mockMvc.perform(query("{\"question\":\"hi\",\"score\":2.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("minScore must be between 0 and 1"));
    }

    @Test
    void servesAQueryWithinTheRules() throws Exception {
        when(vectorSearchPort.search(any(), anyInt())).thenReturn(List.of());
        when(rerankerPort.rerank(any(), any())).thenReturn(List.of());

        mockMvc.perform(query("{\"question\":\"hi\",\"topK\":20}")).andExpect(status().isOk());
    }

    @Test
    void servesAQueryThatOmitsTheOptionalArguments() throws Exception {
        when(vectorSearchPort.search(any(), anyInt())).thenReturn(List.of());
        when(rerankerPort.rerank(any(), any())).thenReturn(List.of());

        mockMvc.perform(query("{\"question\":\"hi\"}")).andExpect(status().isOk());
    }

    @Test
    void rejectsBlankIngestName() throws Exception {
        mockMvc.perform(ingest("{\"name\":\"\",\"content\":\"body\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("document name must not be blank"));
    }

    @Test
    void rejectsBlankIngestContent() throws Exception {
        mockMvc.perform(ingest("{\"name\":\"doc.txt\",\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("document content must not be blank"));
    }

    @Test
    void ingestsADocumentWithinTheRules() throws Exception {
        when(textSplitterPort.split(any())).thenReturn(List.of("body"));
        when(embeddingPort.embed(any())).thenReturn(new float[]{0.1f});

        mockMvc.perform(ingest("{\"name\":\"doc.txt\",\"content\":\"body\"}")).andExpect(status().isOk());
    }
}
