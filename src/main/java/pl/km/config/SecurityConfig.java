package pl.km.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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

import java.util.ArrayList;
import java.util.Arrays;
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
 * <p>Two chains, because the two surfaces demand different audiences:
 * <ul>
 *   <li>{@code /mcp/**} requires {@code keycloak.audience.mcp} ({@code rag-mcp}) —
 *       narrow and MCP-specific, so a token an agent holds cannot be replayed against
 *       the platform's other APIs. It also answers 401 with an RFC 9728
 *       {@code resource_metadata} hint.</li>
 *   <li>everything else requires {@code keycloak.audience.api} ({@code rag-platform}) —
 *       a broad identifier shared by the platform's ordinary APIs, so one token serves
 *       them all.</li>
 * </ul>
 *
 * <p>The realm makes those audiences mutually unreachable rather than merely distinct:
 * each is injected by a client scope ({@code rag-api}, {@code rag-mcp-api}) assigned to
 * exactly one client ({@code rag-api-client}, {@code rag-mcp-client}). Since an optional
 * scope a client does not hold cannot be requested, an agent authenticating through
 * {@code rag-mcp-client} has no way to obtain a {@code rag-platform} token whatever
 * scopes it asks for — the check here enforces a boundary Keycloak already guarantees at
 * issuance. Authorisation on top of that still rests on the {@code rag_admin}/
 * {@code rag_user}/{@code rag_mcp_user} realm roles.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * MCP chain; ordered first so {@code /mcp/**} never falls through to the API chain.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain mcpFilterChain(HttpSecurity http,
                                              @Value("${keycloak.jwk-set-uri}") String jwkSetUri,
                                              @Value("${keycloak.issuer-uri}") String issuerUri,
                                              @Value("${keycloak.audience.mcp}") String audience,
                                              @Value("${mcp.resource}") String resource) throws Exception {
        http
                .securityMatcher("/mcp/**")
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("rag_mcp_user"))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder(jwkSetUri, issuerUri, audienceValidator(audience)))
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(new ResourceMetadataEntryPoint(resource)));
        return http.build();
    }

    /**
     * REST API chain, plus the public RFC 9728 metadata document.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http,
                                              @Value("${keycloak.jwk-set-uri}") String jwkSetUri,
                                              @Value("${keycloak.issuer-uri}") String issuerUri,
                                              @Value("${keycloak.audience.api}") String audience) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Discovery document: readable without a token, by design.
                        .requestMatchers("/.well-known/oauth-protected-resource",
                                "/.well-known/oauth-protected-resource/**")
                        .permitAll()
                        // Method-agnostic: the role is required for every method on these
                        // paths, so a non-POST request cannot slip past the role check.
                        .requestMatchers("/api/documents/ingest", "/api/documents/ingest/file")
                        .hasRole("rag_admin")
                        .requestMatchers("/api/documents/query")
                        .hasRole("rag_user")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder(jwkSetUri, issuerUri, audienceValidator(audience)))
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Decoder that fetches JWKS from the (internal) {@code jwk-set-uri} but validates
     * the {@code iss} claim against the (public) {@code issuer-uri}. Each chain builds
     * its own so they can differ in the extra validators applied.
     */
    @SafeVarargs
    private static JwtDecoder jwtDecoder(String jwkSetUri, String issuerUri,
                                         OAuth2TokenValidator<Jwt>... extraValidators) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(issuerUri));
        validators.addAll(Arrays.asList(extraValidators));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
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
