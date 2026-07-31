package pl.km.rag.application.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.km.rag.application.exception.InvalidInputException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextValueObjectsTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsAbsentOrWhitespaceText(String blank) {
        assertThatThrownBy(() -> new Question(blank))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("question must not be blank");
        assertThatThrownBy(() -> new DocumentName(blank))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("document name must not be blank");
        assertThatThrownBy(() -> new DocumentContent(blank))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("document content must not be blank");
    }

    @Test
    void keepsTextItAccepts() {
        assertThat(new Question("what is rag?").value()).isEqualTo("what is rag?");
        assertThat(new DocumentName("doc.txt").value()).isEqualTo("doc.txt");
        assertThat(new DocumentContent("body").value()).isEqualTo("body");
    }
}
