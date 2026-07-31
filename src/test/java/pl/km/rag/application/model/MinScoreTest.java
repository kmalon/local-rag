package pl.km.rag.application.model;

import org.junit.jupiter.api.Test;
import pl.km.rag.application.exception.InvalidInputException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinScoreTest {

    @Test
    void acceptsTheClosedUnitInterval() {
        assertThat(new MinScore(0).value()).isZero();
        assertThat(new MinScore(0.75).value()).isEqualTo(0.75);
        assertThat(new MinScore(1).value()).isEqualTo(1);
    }

    @Test
    void rejectsThresholdsNoScoreCanBeComparedAgainst() {
        assertThatThrownBy(() -> new MinScore(-0.1))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("minScore must be between 0 and 1");
        assertThatThrownBy(() -> new MinScore(1.1)).isInstanceOf(InvalidInputException.class);
    }
}
