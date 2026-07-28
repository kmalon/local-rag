package pl.km.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import pl.km.rag.RagFacade;
import pl.km.shared.QueryResult;

import java.util.List;

/**
 * Tools published over MCP so external AI agents/LLMs can read the local RAG
 * knowledge base. Read-only: reachable with the {@code rag_mcp_user} realm role.
 */
@Component
public class RagMcpTools {

    static final int DEFAULT_TOP_K = 5;

    private final RagFacade ragFacade;

    public RagMcpTools(RagFacade ragFacade) {
        this.ragFacade = ragFacade;
    }

    @Tool(name = "search_rag_documents",
            description = "Search the local RAG knowledge base and return the matching document chunks, "
                    + "ranked by relevance. Use it to ground answers in the user's own documents.")
    public List<QueryResult> searchDocuments(
            @ToolParam(description = "Natural-language question to search for") String question,
            @ToolParam(description = "Maximum number of chunks to return, default 5", required = false) Integer topK,
            @ToolParam(description = "Minimum relevance score in [0,1]; omit to use the server default",
                    required = false) Double minScore) {
        int effectiveTopK = (topK == null || topK <= 0) ? DEFAULT_TOP_K : topK;
        return ragFacade.search(question, effectiveTopK, minScore);
    }
}
