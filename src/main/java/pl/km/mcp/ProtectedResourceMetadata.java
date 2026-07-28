package pl.km.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OAuth 2.0 Protected Resource Metadata (RFC 9728) describing this MCP server:
 * which resource identifier tokens must be addressed to, and which authorization
 * server issues them.
 */
public record ProtectedResourceMetadata(
        @JsonProperty("resource") String resource,
        @JsonProperty("authorization_servers") List<String> authorizationServers,
        @JsonProperty("bearer_methods_supported") List<String> bearerMethodsSupported) {
}
