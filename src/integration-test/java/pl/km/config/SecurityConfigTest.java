package pl.km.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pl.km.rag.adapter.in.rest.DocumentController;
import pl.km.rag.application.port.in.IngestDocumentPort;
import pl.km.rag.application.port.in.IngestFilePort;
import pl.km.rag.application.port.in.QueryDocumentPort;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "keycloak.issuer-uri=http://localhost:8081/realms/local-rag",
        "keycloak.jwk-set-uri=http://localhost:8081/realms/local-rag/protocol/openid-connect/certs",
        "keycloak.audience=local-rag-api"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestDocumentPort ingestDocumentPort;
    @MockBean
    private IngestFilePort ingestFilePort;
    @MockBean
    private QueryDocumentPort queryDocumentPort;

    private static final String QUERY_BODY = "{\"question\":\"hi\",\"topK\":5,\"score\":null}";
    private static final String INGEST_BODY = "{\"name\":\"doc\",\"content\":\"body\"}";

    @Test
    void queryRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/documents/query")
                        .contentType(MediaType.APPLICATION_JSON).content(QUERY_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void queryAllowedForRagUser() throws Exception {
        when(queryDocumentPort.query(any(), anyInt(), any())).thenReturn(List.of());
        mockMvc.perform(post("/api/documents/query")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_rag_user")))
                        .contentType(MediaType.APPLICATION_JSON).content(QUERY_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void queryForbiddenForRagAdminOnly() throws Exception {
        mockMvc.perform(post("/api/documents/query")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_rag_admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(QUERY_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void ingestAllowedForRagAdmin() throws Exception {
        mockMvc.perform(post("/api/documents/ingest")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_rag_admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(INGEST_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void ingestForbiddenForRagUser() throws Exception {
        mockMvc.perform(post("/api/documents/ingest")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_rag_user")))
                        .contentType(MediaType.APPLICATION_JSON).content(INGEST_BODY))
                .andExpect(status().isForbidden());
    }
}
