package pl.km.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pl.km.rag.adapter.in.rest.DocumentController;
import pl.km.rag.application.port.in.IngestDocumentPort;
import pl.km.rag.application.port.in.IngestFilePort;
import pl.km.rag.application.port.in.QueryDocumentPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the security rules guarding the MCP transport endpoints. The MCP server
 * itself is not part of this web slice, so an authorised request only has to get
 * past the filter chain (it then 404s for want of a handler).
 */
@WebMvcTest(DocumentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "keycloak.issuer-uri=http://localhost:8081/realms/local-rag",
        "keycloak.jwk-set-uri=http://localhost:8081/realms/local-rag/protocol/openid-connect/certs",
        "mcp.resource=http://localhost:8080/mcp"
})
class McpSecurityTest {

    private static final String MCP_SSE = "/mcp/sse";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestDocumentPort ingestDocumentPort;
    @MockBean
    private IngestFilePort ingestFilePort;
    @MockBean
    private QueryDocumentPort queryDocumentPort;

    @Test
    void mcpRequiresAuthentication() throws Exception {
        mockMvc.perform(get(MCP_SSE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedChallengePointsAtResourceMetadata() throws Exception {
        mockMvc.perform(get(MCP_SSE))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString(
                        "resource_metadata=\"http://localhost:8080/.well-known/oauth-protected-resource/mcp\"")));
    }

    @Test
    void mcpForbiddenWithoutMcpRole() throws Exception {
        mockMvc.perform(get(MCP_SSE)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_rag_user"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void mcpAllowedForRagMcpUser() throws Exception {
        mockMvc.perform(get(MCP_SSE)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_rag_mcp_user"))))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }
}
