package pl.km.mcp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Publishes RFC 9728 metadata so an MCP client can discover the authorization server
 * on its own instead of being pre-configured with the Keycloak URL. Unauthenticated
 * by design — a client reads this precisely because it has no usable token yet.
 *
 * <p>Served both at the bare well-known path and at the path-scoped form
 * ({@code /.well-known/oauth-protected-resource/mcp}) that RFC 9728 §3.1 derives for a
 * resource identifier carrying a path. Any other path under the well-known prefix is a
 * 404: the document describes one resource, and answering for a path it does not describe
 * would tell a client its (wrong) resource identifier is the one we protect.
 */
@RestController
public class ProtectedResourceMetadataController {

    private static final String WELL_KNOWN = "/.well-known/oauth-protected-resource";

    private final ProtectedResourceMetadata metadata;
    private final String pathScopedLocation;

    public ProtectedResourceMetadataController(@Value("${mcp.resource}") String resource,
                                               @Value("${keycloak.issuer-uri}") String issuerUri) {
        this.metadata = new ProtectedResourceMetadata(resource, List.of(issuerUri), List.of("header"));
        String resourcePath = URI.create(resource).getPath();
        this.pathScopedLocation = WELL_KNOWN + (resourcePath == null ? "" : resourcePath);
    }

    /**
     * The wildcard is the mapping, not the contract: Spring cannot build a path from
     * {@code mcp.resource} at annotation time, so the exact match happens here — keeping
     * the resource identifier the single source of truth for where this is published.
     */
    @GetMapping({WELL_KNOWN, WELL_KNOWN + "/**"})
    public ResponseEntity<ProtectedResourceMetadata> metadata(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.equals(WELL_KNOWN) && !path.equals(pathScopedLocation)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metadata);
    }
}
