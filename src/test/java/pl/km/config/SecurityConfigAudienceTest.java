package pl.km.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigAudienceTest {

    private static final String AUDIENCE = "rag_mcp";

    private final OAuth2TokenValidator<Jwt> validator = SecurityConfig.audienceValidator(AUDIENCE);

    private static Jwt jwtWithAudience(List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("user");
        if (audience != null) {
            builder.claim("aud", audience);
        }
        return builder.build();
    }

    @Test
    void acceptsTokenAddressedToThisServer() {
        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience(List.of(AUDIENCE)));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void acceptsTokenListingThisServerAmongSeveralAudiences() {
        OAuth2TokenValidatorResult result =
                validator.validate(jwtWithAudience(List.of("rag-platform", AUDIENCE)));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenMintedForAnotherAudience() {
        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience(List.of("rag-platform")));

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWithoutAudienceClaim() {
        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience(null));

        assertThat(result.hasErrors()).isTrue();
    }
}
