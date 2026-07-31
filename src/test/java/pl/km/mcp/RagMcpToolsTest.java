package pl.km.mcp;

import org.junit.jupiter.api.Test;
import pl.km.shared.rag.RagQueryResult;
import pl.km.shared.rag.RagFacade;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RagMcpToolsTest {

    private final RagFacade ragFacade = mock(RagFacade.class);
    private final RagMcpTools tools = new RagMcpTools(ragFacade);

    @Test
    void passesArgumentsToFacadeAndReturnsResults() {
        List<RagQueryResult> expected = List.of(new RagQueryResult("doc.txt", "body", 0.9));
        when(ragFacade.search(any(), any(), any())).thenReturn(expected);

        List<RagQueryResult> actual = tools.searchDocuments("question", 3, 0.5);

        assertThat(actual).isEqualTo(expected);
        verify(ragFacade).search("question", 3, 0.5);
    }

    @Test
    void forwardsAbsentArgumentsWithoutSubstitutingDefaults() {
        tools.searchDocuments("question", null, null);

        verify(ragFacade).search("question", null, null);
    }

    @Test
    void forwardsOutOfRangeArgumentsForTheSearchToReject() {
        tools.searchDocuments("question", 100_000, null);

        verify(ragFacade).search("question", 100_000, null);
    }
}
