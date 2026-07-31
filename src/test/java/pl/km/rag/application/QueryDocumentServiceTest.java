package pl.km.rag.application;

import org.junit.jupiter.api.Test;
import pl.km.rag.application.exception.InvalidInputException;
import pl.km.rag.application.model.QueryResult;
import pl.km.rag.application.model.SearchLimits;
import pl.km.rag.application.model.TopK;
import pl.km.rag.application.port.out.RerankerPort;
import pl.km.rag.application.port.out.VectorSearchPort;
import pl.km.rag.config.QueryProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueryDocumentServiceTest {

    private final VectorSearchPort vectorSearchPort = mock(VectorSearchPort.class);
    private final RerankerPort rerankerPort = mock(RerankerPort.class);
    private final QueryProperties queryProperties = new QueryProperties(0.75, 4, 20, 80);
    private final QueryDocumentService service =
            new QueryDocumentService(vectorSearchPort, rerankerPort, queryProperties);

    private static QueryResult r(String name, double score) {
        return new QueryResult(name, name + "-content", score);
    }

    @Test
    void publishesTheLimitsItEnforces() {
        assertThat(service.limits()).isEqualTo(new SearchLimits(TopK.DEFAULT, 20));

        QueryProperties tight = new QueryProperties(0.75, 4, 8, 8);
        SearchLimits limits = new QueryDocumentService(vectorSearchPort, rerankerPort, tight).limits();

        assertThat(limits.maxTopK()).isEqualTo(2);
        assertThat(limits.defaultTopK()).isEqualTo(2); // the default cannot exceed the ceiling
    }

    @Test
    void overFetchesFarMoreCandidatesThanTopK() {
        when(vectorSearchPort.search(any(), anyInt())).thenReturn(List.of());
        when(rerankerPort.rerank(any(), any())).thenReturn(List.of());

        service.query("q", 3, 0.0);

        verify(vectorSearchPort).search(eq("q"), eq(20));
    }

    @Test
    void filtersByRerankerScoreAndLimitsToTopK() {
        when(vectorSearchPort.search(any(), anyInt())).thenReturn(List.of(r("a", 0.1), r("b", 0.1), r("c", 0.1)));
        when(rerankerPort.rerank(any(), any()))
                .thenReturn(List.of(r("b", 0.95), r("a", 0.80), r("c", 0.40)));

        List<QueryResult> result = service.query("q", 2, null); // default threshold 0.75

        assertThat(result).extracting(QueryResult::name).containsExactly("b", "a");
        assertThat(result).allSatisfy(qr -> assertThat(qr.score()).isGreaterThanOrEqualTo(0.75));
    }

    @Test
    void rejectsTopKBeyondWhatTheRatioCanCover() {
        assertThatThrownBy(() -> service.query("q", 50, 0.0))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("topK must be between 1 and 20");

        verifyNoInteractions(vectorSearchPort, rerankerPort);
    }

    @Test
    void poolGrowsWithTopKUpToTheCostCeiling() {
        when(vectorSearchPort.search(any(), anyInt())).thenReturn(List.of());
        when(rerankerPort.rerank(any(), any())).thenReturn(List.of());

        service.query("q", 10, 0.0);
        verify(vectorSearchPort).search(eq("q"), eq(40));

        service.query("q", 20, 0.0);
        verify(vectorSearchPort).search(eq("q"), eq(80));
    }

    @Test
    void poolNeverFallsBelowTheFloor() {
        when(vectorSearchPort.search(any(), anyInt())).thenReturn(List.of());
        when(rerankerPort.rerank(any(), any())).thenReturn(List.of());

        service.query("q", 1, 0.0);

        verify(vectorSearchPort).search(eq("q"), eq(20));
    }

    @Test
    void appliesTheDefaultTopKWhenNoneIsRequested() {
        when(vectorSearchPort.search(any(), anyInt())).thenReturn(List.of());
        when(rerankerPort.rerank(any(), any()))
                .thenReturn(List.of(r("a", 0.9), r("b", 0.9), r("c", 0.9), r("d", 0.9), r("e", 0.9), r("f", 0.9)));

        assertThat(service.query("q", null, 0.0)).hasSize(TopK.DEFAULT);
    }

    @Test
    void rejectsInputTheDomainForbids() {
        assertThatThrownBy(() -> service.query("  ", 5, 0.0)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> service.query("q", 0, 0.0)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> service.query("q", 5, 2.0)).isInstanceOf(InvalidInputException.class);

        verifyNoInteractions(vectorSearchPort, rerankerPort);
    }

    @Test
    void emptyCandidatePoolYieldsEmptyResult() {
        when(vectorSearchPort.search(any(), anyInt())).thenReturn(List.of());
        when(rerankerPort.rerank(any(), any())).thenReturn(List.of());

        assertThat(service.query("q", 5, null)).isEmpty();
    }

    @Test
    void scoreZeroDisablesFiltering() {
        when(vectorSearchPort.search(any(), anyInt())).thenReturn(List.of(r("a", 0.0)));
        when(rerankerPort.rerank(any(), any()))
                .thenReturn(List.of(r("a", 0.9), r("b", 0.5), r("c", 0.01)));

        List<QueryResult> result = service.query("q", 10, 0.0);

        assertThat(result).extracting(QueryResult::name).containsExactly("a", "b", "c");
    }
}
