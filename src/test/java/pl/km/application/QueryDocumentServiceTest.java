package pl.km.application;

import org.junit.jupiter.api.Test;
import pl.km.application.QueryDocumentService;
import pl.km.application.port.out.RerankerPort;
import pl.km.application.port.out.VectorSearchPort;
import pl.km.config.QueryProperties;
import pl.km.application.model.QueryResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryDocumentServiceTest {

    private final VectorSearchPort vectorSearchPort = mock(VectorSearchPort.class);
    private final RerankerPort rerankerPort = mock(RerankerPort.class);
    private final QueryProperties queryProperties = new QueryProperties(0.75, 20);
    private final QueryDocumentService service =
            new QueryDocumentService(vectorSearchPort, rerankerPort, queryProperties);

    private static QueryResult r(String name, double score) {
        return new QueryResult(name, name + "-content", score);
    }

    @Test
    void overFetchesCandidatePoolNotTopK() {
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
    void poolIsClampedToTopKWhenTopKExceedsConfiguredPool() {
        when(vectorSearchPort.search(any(), anyInt())).thenReturn(List.of());
        when(rerankerPort.rerank(any(), any())).thenReturn(List.of());

        service.query("q", 50, 0.0);

        verify(vectorSearchPort).search(eq("q"), eq(50));
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
