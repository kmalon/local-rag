package pl.km.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.net.URI;

/**
 * Adds the RFC 9728 {@code resource_metadata} hint to the challenge sent on 401, so a
 * client that hits the MCP server without a usable token learns where to look up the
 * authorization server. Wraps the standard bearer-token entry point rather than
 * replacing it, keeping its {@code error}/{@code error_description} parameters.
 */
class ResourceMetadataEntryPoint implements AuthenticationEntryPoint {

    private static final String WELL_KNOWN = "/.well-known/oauth-protected-resource";

    private final AuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
    private final String metadataUri;

    ResourceMetadataEntryPoint(String resource) {
        this.metadataUri = metadataUriFor(resource);
    }

    /**
     * RFC 9728 §3.1: the well-known segment is inserted between the host and the
     * resource's path, so {@code http://host/mcp} is described at
     * {@code http://host/.well-known/oauth-protected-resource/mcp}.
     */
    static String metadataUriFor(String resource) {
        URI uri = URI.create(resource);
        String path = uri.getPath() == null ? "" : uri.getPath();
        return uri.getScheme() + "://" + uri.getAuthority() + WELL_KNOWN + path;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        delegate.commence(request, response, authException);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                withResourceMetadata(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)));
    }

    private String withResourceMetadata(String challenge) {
        String parameter = "resource_metadata=\"" + metadataUri + "\"";
        if (challenge == null || challenge.isBlank() || challenge.trim().equalsIgnoreCase("Bearer")) {
            return "Bearer " + parameter;
        }
        return challenge + ", " + parameter;
    }
}
