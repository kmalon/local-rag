package pl.km.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Publishes RFC 9728 metadata so an MCP client can discover the authorization server
 * on its own instead of being pre-configured with the Keycloak URL. Unauthenticated
 * by design — a client reads this precisely because it has no usable token yet.
 *
 * <p>Served both at the bare well-known path and at the path-scoped form
 * ({@code /.well-known/oauth-protected-resource/mcp}) that RFC 9728 §3.1 derives for a
 * resource identifier carrying a path.
 */
@RestController
public class ProtectedResourceMetadataController {

    private final ProtectedResourceMetadata metadata;

    public ProtectedResourceMetadataController(@Value("${mcp.resource}") String resource,
                                               @Value("${keycloak.issuer-uri}") String issuerUri) {
        this.metadata = new ProtectedResourceMetadata(resource, List.of(issuerUri), List.of("header"));
    }

    @GetMapping({"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/**"})
    public ProtectedResourceMetadata metadata() {
        return metadata;
    }
}
