package pl.km.mcp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import pl.km.mcp.RagMcpTools;
import pl.km.shared.rag.RagFacade;
import pl.km.shared.rag.RagSearchLimits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpServerConfigTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RagFacade ragFacade = mock(RagFacade.class);

    private ToolCallback searchTool(RagSearchLimits limits) {
        when(ragFacade.limits()).thenReturn(limits);
        ToolCallback[] callbacks = new McpServerConfig()
                .ragToolCallbacks(new RagMcpTools(ragFacade), ragFacade)
                .getToolCallbacks();
        assertThat(callbacks).hasSize(1);
        return callbacks[0];
    }

    private JsonNode topKSchema(RagSearchLimits limits) throws Exception {
        return JSON.readTree(searchTool(limits).getToolDefinition().inputSchema()).path("properties").path("topK");
    }

    @Test
    void keepsTheToolIdentityGeneratedFromTheAnnotations() {
        assertThat(searchTool(new RagSearchLimits(5, 20)).getToolDefinition().name())
                .isEqualTo("search_rag_documents");
    }

    @Test
    void publishesTheConfiguredRangeAsSchemaBounds() throws Exception {
        JsonNode topK = topKSchema(new RagSearchLimits(5, 20));

        assertThat(topK.path("minimum").asInt()).isEqualTo(1);
        assertThat(topK.path("maximum").asInt()).isEqualTo(20);
    }

    @Test
    void boundsFollowTheDeploymentRatherThanAConstant() throws Exception {
        assertThat(topKSchema(new RagSearchLimits(5, 40)).path("maximum").asInt()).isEqualTo(40);
    }

    @Test
    void statesTheRangeInProseTooForModelsThatOnlyReadDescriptions() throws Exception {
        assertThat(topKSchema(new RagSearchLimits(5, 20)).path("description").asText())
                .contains("Maximum number of chunks to return")
                .contains("1-20")
                .contains("defaults to 5");
    }

    @Test
    void leavesTheOtherParametersAsGenerated() throws Exception {
        JsonNode properties = JSON.readTree(searchTool(new RagSearchLimits(5, 20))
                .getToolDefinition().inputSchema()).path("properties");

        assertThat(properties.has("question")).isTrue();
        assertThat(properties.has("minScore")).isTrue();
        assertThat(properties.path("minScore").has("maximum")).isFalse();
    }
}
