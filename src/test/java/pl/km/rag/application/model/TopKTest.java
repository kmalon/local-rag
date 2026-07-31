package pl.km.rag.application.model;

import org.junit.jupiter.api.Test;
import pl.km.rag.application.exception.InvalidInputException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopKTest {

    private static final int POOL = 20;

    @Test
    void rejectsValuesBelowOneWhereverItIsConstructed() {
        assertThatThrownBy(() -> new TopK(0)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> new TopK(-1)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> TopK.boundedBy(0, POOL)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void acceptsTheWholeServableRange() {
        assertThat(TopK.boundedBy(1, POOL).value()).isEqualTo(1);
        assertThat(TopK.boundedBy(POOL, POOL).value()).isEqualTo(POOL);
    }

    @Test
    void rejectsMoreThanTheServerCanServe() {
        assertThatThrownBy(() -> TopK.boundedBy(POOL + 1, POOL))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("topK must be between 1 and " + POOL);
    }

    @Test
    void rejectionNamesTheLimitThatApplies() {
        assertThatThrownBy(() -> TopK.boundedBy(50, 5)).hasMessage("topK must be between 1 and 5");
    }

    @Test
    void appliesTheDefaultWhenNothingIsRequested() {
        assertThat(TopK.boundedBy(null, POOL).value()).isEqualTo(TopK.DEFAULT);
    }

    @Test
    void boundsTheDefaultByTheServableRangeToo() {
        assertThat(TopK.boundedBy(null, 3).value()).isEqualTo(3);
    }
}
