package pl.km.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.km.shared.rag.RagQueryResult;
import pl.km.shared.rag.RagFacade;

import java.util.List;

/**
 * Tools published over MCP so external AI agents/LLMs can read the local RAG
 * knowledge base. Read-only: reachable with the {@code rag_mcp_user} realm role.
 */
@Component
public class RagMcpTools {

    private static final Logger log = LoggerFactory.getLogger(RagMcpTools.class);

    static final int DEFAULT_TOP_K = 5;

    private final RagFacade ragFacade;
    private final int maxTopK;

    public RagMcpTools(RagFacade ragFacade, @Value("${mcp.search.max-top-k}") int maxTopK) {
        if (maxTopK < 1) {
            throw new IllegalArgumentException("mcp.search.max-top-k must be >= 1");
        }
        this.ragFacade = ragFacade;
        this.maxTopK = maxTopK;
    }

    @Tool(name = "search_rag_documents",
            description = "Search the local RAG knowledge base and return the matching document chunks, "
                    + "ranked by relevance. Use it to ground answers in the user's own documents.")
    public List<RagQueryResult> searchDocuments(
            @ToolParam(description = "Natural-language question to search for") String question,
            @ToolParam(description = "Maximum number of chunks to return, default 5, at most 20 "
                    + "unless the server is configured otherwise; larger values are reduced to the limit",
                    required = false) Integer topK,
            @ToolParam(description = "Minimum relevance score in [0,1]; omit to use the server default",
                    required = false) Double minScore) {
        return ragFacade.search(question, effectiveTopK(topK), minScore);
    }

    /**
     * Bounds the caller's {@code topK} at both ends. The upper bound is the load guard:
     * the retrieval pipeline over-fetches at least {@code topK} candidates from pgvector and
     * runs the cross-encoder once per candidate, sequentially, on the calling thread — so an
     * agent asking for {@code topK: 100000} would cost that many inferences. An LLM chooses
     * this argument, which makes an implausible value likelier here than on the REST API,
     * and clamping keeps the tool usable where rejecting would just cost a round trip.
     */
    private int effectiveTopK(Integer requested) {
        int topK = (requested == null || requested <= 0) ? DEFAULT_TOP_K : requested;
        if (topK > maxTopK) {
            log.debug("MCP search requested topK={}, reduced to the configured maximum {}", topK, maxTopK);
        }
        return Math.min(topK, maxTopK);
    }
}
