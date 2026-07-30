package pl.km.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pl.km.config.SecurityConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProtectedResourceMetadataController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "keycloak.issuer-uri=http://localhost:8081/realms/local-rag",
        "keycloak.jwk-set-uri=http://localhost:8081/realms/local-rag/protocol/openid-connect/certs",
        "keycloak.audience.api=rag-platform",
        "keycloak.audience.mcp=rag-mcp",
        "mcp.resource=http://localhost:8080/mcp"
})
class ProtectedResourceMetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void metadataIsReadableWithoutAToken() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("http://localhost:8080/mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]")
                        .value("http://localhost:8081/realms/local-rag"))
                .andExpect(jsonPath("$.bearer_methods_supported[0]").value("header"));
    }

    @Test
    void metadataIsAlsoServedAtThePathScopedLocation() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("http://localhost:8080/mcp"));
    }
}
