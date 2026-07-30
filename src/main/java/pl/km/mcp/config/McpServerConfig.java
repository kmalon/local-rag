package pl.km.mcp.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.km.mcp.RagMcpTools;

/**
 * Registers the RAG tools with the Spring AI MCP server (stateless Streamable HTTP
 * transport, see {@code spring.ai.mcp.server.*} in application.yml).
 * <p>
 * The same {@link ToolCallbackProvider} bean serves either protocol: Spring AI's
 * stateless auto-configuration converts the callbacks into stateless tool
 * specifications, so switching transports needs no change here.
 */
@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider ragToolCallbacks(RagMcpTools ragMcpTools) {
        return MethodToolCallbackProvider.builder().toolObjects(ragMcpTools).build();
    }
}
