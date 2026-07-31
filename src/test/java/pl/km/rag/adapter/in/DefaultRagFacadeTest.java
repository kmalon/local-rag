package pl.km.rag.adapter.in;

import org.junit.jupiter.api.Test;
import pl.km.rag.application.exception.InvalidInputException;
import pl.km.rag.application.exception.RerankerException;
import pl.km.rag.application.model.QueryResult;
import pl.km.rag.application.model.SearchLimits;
import pl.km.rag.application.port.in.QueryDocumentPort;
import pl.km.shared.rag.RagQueryResult;
import pl.km.shared.rag.RagSearchArgumentException;
import pl.km.shared.rag.RagSearchLimits;
import pl.km.shared.rag.RagSearchUnavailableException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultRagFacadeTest {

    private final QueryDocumentPort queryDocumentPort = mock(QueryDocumentPort.class);
    private final DefaultRagFacade facade = new DefaultRagFacade(queryDocumentPort);

    @Test
    void mapsInternalResultsOntoTheContractType() {
        when(queryDocumentPort.query(any(), any(), any()))
                .thenReturn(List.of(new QueryResult("doc.txt", "body", 0.9)));

        List<RagQueryResult> results = facade.search("question", 3, 0.5);

        assertThat(results).containsExactly(new RagQueryResult("doc.txt", "body", 0.9));
    }

    @Test
    void publishesTheUseCaseLimitsOnTheContract() {
        when(queryDocumentPort.limits()).thenReturn(new SearchLimits(5, 20));

        assertThat(facade.limits()).isEqualTo(new RagSearchLimits(5, 20));
    }

    @Test
    void translatesRerankerFailureIntoTheContractException() {
        RerankerException cause = new RerankerException("Cross-encoder inference failed", new IllegalStateException());
        when(queryDocumentPort.query(any(), any(), any())).thenThrow(cause);

        assertThatThrownBy(() -> facade.search("question", 3, null))
                .isInstanceOf(RagSearchUnavailableException.class)
                .hasCauseReference(cause)
                .hasMessageContaining("reranking failed")
                .hasMessageContaining("Retry");
    }

    @Test
    void translatesRejectedInputIntoTheArgumentExceptionKeepingTheMessage() {
        InvalidInputException cause = new InvalidInputException("topK must be between 1 and 20");
        when(queryDocumentPort.query(any(), any(), any())).thenThrow(cause);

        assertThatThrownBy(() -> facade.search("question", 50, null))
                .isInstanceOf(RagSearchArgumentException.class)
                .hasCauseReference(cause)
                .hasMessage("topK must be between 1 and 20");
    }

    @Test
    void translatesUnexpectedFailureIntoTheContractException() {
        RuntimeException cause = new IllegalStateException("pgvector connection refused at 10.0.0.1:5432");
        when(queryDocumentPort.query(any(), any(), any())).thenThrow(cause);

        assertThatThrownBy(() -> facade.search("question", 3, null))
                .isInstanceOf(RagSearchUnavailableException.class)
                .hasCauseReference(cause)
                .hasMessageNotContaining("pgvector");
    }
}
