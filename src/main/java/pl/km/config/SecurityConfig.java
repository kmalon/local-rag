package pl.km.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Secures the REST API and the MCP server as an OAuth2 Resource Server validating
 * Keycloak-issued JWTs. Ingestion endpoints require the {@code rag_admin} realm role;
 * the query (read) endpoint requires {@code rag_user}; the MCP endpoints require
 * {@code rag_mcp_user}. Keycloak places realm roles under the
 * {@code realm_access.roles} claim, so a custom converter maps them to Spring
 * {@code ROLE_}-prefixed authorities.
 *
 * <p>Signing keys are fetched from {@code keycloak.jwk-set-uri} (reachable over the
 * docker network) while the token {@code iss} claim is validated against
 * {@code keycloak.issuer-uri} (the public host URL).
 *
 * <p>Tokens must also be addressed to this resource server: the {@code aud} claim has
 * to contain {@code keycloak.audience}. Without that check any token minted in the
 * realm — including one issued to an unrelated client — would be accepted here, which
 * is the confused-deputy risk the MCP authorization spec (RFC 8707 resource
 * indicators) exists to close.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Method-agnostic: the role is required for every method on these
                        // paths, so a non-POST request cannot slip past the role check.
                        .requestMatchers("/api/documents/ingest", "/api/documents/ingest/file")
                        .hasRole("rag_admin")
                        .requestMatchers("/api/documents/query")
                        .hasRole("rag_user")
                        // MCP server transport (SSE stream + message endpoint).
                        .requestMatchers("/mcp/**")
                        .hasRole("rag_mcp_user")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Decoder that fetches JWKS from the (internal) {@code jwk-set-uri} but validates
     * the {@code iss} claim against the (public) {@code issuer-uri}, plus the
     * {@code aud} claim against this server's own identifier.
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${keycloak.jwk-set-uri}") String jwkSetUri,
                                 @Value("${keycloak.issuer-uri}") String issuerUri,
                                 @Value("${keycloak.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                audienceValidator(audience));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /**
     * Rejects tokens whose {@code aud} claim does not list this resource server.
     * A missing claim fails too: Keycloak only emits {@code aud} when an audience
     * mapper is configured, so its absence means the token was never scoped to us.
     */
    static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(audience));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::extractRealmRoles);
        return converter;
    }

    private static Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Object realmAccessClaim = jwt.getClaim("realm_access");
        if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
            log.debug("JWT for subject '{}' has no usable realm_access claim; no realm roles granted",
                    jwt.getSubject());
            return List.of();
        }
        Object roles = realmAccess.get("roles");
        if (!(roles instanceof Collection<?> roleList)) {
            log.debug("realm_access.roles missing or not a collection for subject '{}'; no realm roles granted",
                    jwt.getSubject());
            return List.of();
        }
        return roleList.stream()
                .map(Object::toString)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
