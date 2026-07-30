package pl.km.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.util.MimeType;
import pl.km.rag.adapter.in.DefaultRagFacade;
import pl.km.rag.application.exception.RerankerException;
import pl.km.rag.application.model.QueryResult;
import pl.km.rag.application.port.in.QueryDocumentPort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives a {@code tools/call} of {@code search_rag_documents} through the real Spring AI +
 * MCP SDK conversion chain — {@code MethodToolCallback} → {@code ToolExecutionException} →
 * {@link McpToolUtils} → {@link McpSchema.CallToolResult} — with only the RAG port stubbed.
 * <p>
 * What it pins down: a reranker failure reaches the agent as a tool result with
 * {@code isError=true} whose text is the message chosen in {@link DefaultRagFacade}, not the
 * internal cause. That is what makes the wording of those constants part of the contract.
 * <p>
 * A unit test despite exercising third-party code: it starts no Spring context, which is the
 * line this project draws between {@code src/test} and {@code src/integration-test}. The
 * Streamable HTTP transport in front of it is covered by {@code McpSecurityTest}.
 */
class RagMcpToolErrorReportingTest {

    private static final MimeType JSON = MimeType.valueOf("application/json");

    private final QueryDocumentPort queryDocumentPort = mock(QueryDocumentPort.class);

    private McpStatelessServerFeatures.SyncToolSpecification searchTool() {
        RagMcpTools tools = new RagMcpTools(new DefaultRagFacade(queryDocumentPort), 20);
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks();
        assertThat(callbacks).hasSize(1);
        return McpToolUtils.toStatelessSyncToolSpecification(callbacks[0], JSON);
    }

    private McpSchema.CallToolResult callSearch() {
        return searchTool().callHandler().apply(
                McpTransportContext.EMPTY,
                new McpSchema.CallToolRequest("search_rag_documents", Map.of("question", "anything")));
    }

    private static String textOf(McpSchema.CallToolResult result) {
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }

    @Test
    void toolIsExposedUnderItsPublishedName() {
        assertThat(searchTool().tool().name()).isEqualTo("search_rag_documents");
    }

    @Test
    void rerankerFailureIsReportedAsAToolErrorNotATransportFailure() {
        when(queryDocumentPort.query(any(), anyInt(), any()))
                .thenThrow(new RerankerException("Cross-encoder inference failed", new IllegalStateException()));

        McpSchema.CallToolResult result = callSearch();

        assertThat(result.isError()).isTrue();
        assertThat(textOf(result))
                .contains("relevance reranking failed")
                .contains("Retry");
    }

    @Test
    void toolErrorDoesNotLeakInternalDetail() {
        when(queryDocumentPort.query(any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("pgvector connection refused at 10.0.0.1:5432"));

        assertThat(textOf(callSearch()))
                .doesNotContain("pgvector")
                .doesNotContain("10.0.0.1")
                .doesNotContain("IllegalStateException");
    }

    @Test
    void successfulSearchIsNotFlaggedAsAnError() {
        when(queryDocumentPort.query(any(), anyInt(), any()))
                .thenReturn(List.of(new QueryResult("doc.txt", "body", 0.9)));

        McpSchema.CallToolResult result = callSearch();

        assertThat(result.isError()).isFalse();
        assertThat(textOf(result)).contains("doc.txt").contains("body");
    }
}
