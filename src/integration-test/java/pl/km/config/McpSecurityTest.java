package pl.km.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.km.rag.adapter.in.rest.DocumentController;
import pl.km.rag.application.port.in.IngestDocumentPort;
import pl.km.rag.application.port.in.IngestFilePort;
import pl.km.rag.application.port.in.QueryDocumentPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the security rules guarding the MCP transport endpoint. The MCP server
 * itself is not part of this web slice, so an authorised request only has to get
 * past the filter chain (it then 404s for want of a handler).
 * <p>
 * The transport is stateless Streamable HTTP: one {@code POST /mcp} per JSON-RPC
 * message, so the token is checked on every call rather than once per session.
 */
@WebMvcTest(DocumentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "keycloak.issuer-uri=http://localhost:8081/realms/local-rag",
        "keycloak.jwk-set-uri=http://localhost:8081/realms/local-rag/protocol/openid-connect/certs",
        "keycloak.audience.api=rag-platform",
        "keycloak.audience.mcp=rag-mcp",
        "mcp.resource=http://localhost:8080/mcp"
})
class McpSecurityTest {

    private static final String MCP = "/mcp";
    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestDocumentPort ingestDocumentPort;
    @MockitoBean
    private IngestFilePort ingestFilePort;
    @MockitoBean
    private QueryDocumentPort queryDocumentPort;

    private static MockHttpServletRequestBuilder mcpCall() {
        return post(MCP).contentType(MediaType.APPLICATION_JSON).content(INITIALIZE);
    }

    @Test
    void mcpRequiresAuthentication() throws Exception {
        mockMvc.perform(mcpCall())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedChallengePointsAtResourceMetadata() throws Exception {
        mockMvc.perform(mcpCall())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString(
                        "resource_metadata=\"http://localhost:8080/.well-known/oauth-protected-resource/mcp\"")));
    }

    @Test
    void mcpForbiddenWithoutMcpRole() throws Exception {
        mockMvc.perform(mcpCall()
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_rag_user"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void mcpAllowedForRagMcpUser() throws Exception {
        mockMvc.perform(mcpCall()
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_rag_mcp_user"))))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }

    /**
     * The endpoint is the bare {@code /mcp} path, so the chain must claim it without
     * relying on {@code /mcp/**} matching a path with no trailing segment.
     */
    @Test
    void everyMethodOnTheBareMcpPathIsGuarded() throws Exception {
        mockMvc.perform(post(MCP)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(MCP)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(MCP)).andExpect(status().isUnauthorized());
    }

    /**
     * The SSE transport this endpoint replaced is gone; nothing under {@code /mcp/}
     * may become reachable without the MCP audience and role.
     */
    @Test
    void retiredSseSubPathsStayGuarded() throws Exception {
        mockMvc.perform(get("/mcp/sse")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/mcp/message")).andExpect(status().isUnauthorized());
    }
}
