package pl.km.mcp;

import org.junit.jupiter.api.Test;
import pl.km.shared.rag.RagQueryResult;
import pl.km.shared.rag.RagFacade;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class RagMcpToolsTest {

    private final RagFacade ragFacade = mock(RagFacade.class);
    private final RagMcpTools tools = new RagMcpTools(ragFacade);

    @Test
    void passesArgumentsToFacadeAndReturnsResults() {
        List<RagQueryResult> expected = List.of(new RagQueryResult("doc.txt", "body", 0.9));
        when(ragFacade.search(any(), anyInt(), any())).thenReturn(expected);

        List<RagQueryResult> actual = tools.searchDocuments("question", 3, 0.5);

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
