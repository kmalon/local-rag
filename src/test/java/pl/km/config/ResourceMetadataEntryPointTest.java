package pl.km.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceMetadataEntryPointTest {

    @Test
    void insertsWellKnownSegmentBeforeTheResourcePath() {
        assertThat(ResourceMetadataEntryPoint.metadataUriFor("http://localhost:8080/mcp"))
                .isEqualTo("http://localhost:8080/.well-known/oauth-protected-resource/mcp");
    }

    @Test
    void handlesResourceWithoutPath() {
        assertThat(ResourceMetadataEntryPoint.metadataUriFor("https://rag.example.com"))
                .isEqualTo("https://rag.example.com/.well-known/oauth-protected-resource");
    }
}
