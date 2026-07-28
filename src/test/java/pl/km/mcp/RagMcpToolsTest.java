package pl.km.mcp;

import org.junit.jupiter.api.Test;
import pl.km.rag.RagFacade;
import pl.km.shared.QueryResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagMcpToolsTest {

    private final RagFacade ragFacade = mock(RagFacade.class);
    private final RagMcpTools tools = new RagMcpTools(ragFacade);

    @Test
    void passesArgumentsToFacadeAndReturnsResults() {
        List<QueryResult> expected = List.of(new QueryResult("doc.txt", "body", 0.9));
        when(ragFacade.search(any(), anyInt(), any())).thenReturn(expected);

        List<QueryResult> actual = tools.searchDocuments("question", 3, 0.5);

        assertThat(actual).isEqualTo(expected);
        verify(ragFacade).search("question", 3, 0.5);
    }

    @Test
    void usesDefaultTopKWhenNotProvided() {
        tools.searchDocuments("question", null, null);

        verify(ragFacade).search("question", RagMcpTools.DEFAULT_TOP_K, null);
    }

    @Test
    void usesDefaultTopKWhenNotPositive() {
        tools.searchDocuments("question", 0, null);

        verify(ragFacade).search("question", RagMcpTools.DEFAULT_TOP_K, null);
    }
}
