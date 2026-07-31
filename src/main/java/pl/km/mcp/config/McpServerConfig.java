package pl.km.mcp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import pl.km.mcp.RagMcpTools;
import pl.km.shared.rag.RagFacade;
import pl.km.shared.rag.RagSearchLimits;

import java.lang.reflect.Method;

@Configuration
public class McpServerConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SEARCH_METHOD = "searchDocuments";
    private static final String TOP_K = "topK";

    @Bean
    public ToolCallbackProvider ragToolCallbacks(RagMcpTools ragMcpTools, RagFacade ragFacade) {
        Method searchDocuments = ReflectionUtils.findMethod(
                RagMcpTools.class, SEARCH_METHOD, String.class, Integer.class, Double.class);
        Assert.notNull(searchDocuments, "RagMcpTools." + SEARCH_METHOD + " not found");

        ToolDefinition generated = ToolDefinitions.from(searchDocuments);
        ToolDefinition published = DefaultToolDefinition.builder()
                .name(generated.name())
                .description(generated.description())
                .inputSchema(withTopKBounds(generated.inputSchema(), ragFacade.limits()))
                .build();

        ToolCallback callback = MethodToolCallback.builder()
                .toolDefinition(published)
                .toolMethod(searchDocuments)
                .toolObject(ragMcpTools)
                .build();
        return ToolCallbackProvider.from(callback);
    }

    private static String withTopKBounds(String inputSchema, RagSearchLimits limits) {
        try {
            ObjectNode schema = (ObjectNode) JSON.readTree(inputSchema);
            JsonNode properties = schema.get("properties");
            Assert.isTrue(properties != null && properties.has(TOP_K),
                    "generated tool schema has no '" + TOP_K + "' property");

            ObjectNode topK = (ObjectNode) properties.get(TOP_K);
            topK.put("minimum", 1);
            topK.put("maximum", limits.maxTopK());
            topK.put("description", topK.path("description").asText("")
                    + " Allowed range: 1-" + limits.maxTopK()
                    + "; omitted defaults to " + limits.defaultTopK() + ".");
            return JSON.writeValueAsString(schema);
        } catch (Exception e) {
            throw new IllegalStateException("Could not publish the MCP tool schema", e);
        }
    }
}
