package pl.km.rag.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryPropertiesTest {

    private static final QueryProperties PROPERTIES = new QueryProperties(0.75, 4, 20, 80);

    @Test
    void derivesTheLargestServableTopKFromTheCostCeiling() {
        assertThat(PROPERTIES.maxTopK()).isEqualTo(20);
    }

    @Test
    void largestServableTopKNeverOverrunsTheCeiling() {
        QueryProperties awkward = new QueryProperties(0.5, 3, 10, 100);

        assertThat(awkward.maxTopK()).isEqualTo(33);
        assertThat(awkward.poolSizeFor(awkward.maxTopK())).isLessThanOrEqualTo(100);
    }

    @Test
    void scalesThePoolWithTheRequest() {
        assertThat(PROPERTIES.poolSizeFor(10)).isEqualTo(40);
        assertThat(PROPERTIES.poolSizeFor(15)).isEqualTo(60);
    }

    @Test
    void holdsThePoolBetweenTheFloorAndTheCeiling() {
        assertThat(PROPERTIES.poolSizeFor(1)).isEqualTo(20);    // 4 lifted to the floor
        assertThat(PROPERTIES.poolSizeFor(20)).isEqualTo(80);   // exactly the ceiling
    }

    @Test
    void rejectsConfigurationThatCouldNotServeAnything() {
        assertThatThrownBy(() -> new QueryProperties(0.75, 0, 20, 80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("over-fetch-factor");
        assertThatThrownBy(() -> new QueryProperties(0.75, 4, 0, 80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("min-candidates");
        assertThatThrownBy(() -> new QueryProperties(0.75, 4, 40, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-candidates");
        assertThatThrownBy(() -> new QueryProperties(0.75, 30, 2, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-candidates");
        assertThatThrownBy(() -> new QueryProperties(1.5, 4, 20, 80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default-score-threshold");
    }
}
