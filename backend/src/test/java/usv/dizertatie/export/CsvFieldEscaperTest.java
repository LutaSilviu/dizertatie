package usv.dizertatie.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvFieldEscaperTest {

    private final CsvFieldEscaper escaper = new CsvFieldEscaper();

    @Test
    void escape_nullValue_returnsEmptyCell() {
        assertThat(escaper.escape(null)).isEmpty();
    }

    @Test
    void escape_valueWithCommaQuoteAndNewline_quotesAndDoublesQuotes() {
        assertThat(escaper.escape("a, \"b\"\nc")).isEqualTo("\"a, \"\"b\"\"\nc\"");
    }

    @Test
    void escape_plainValue_leavesItUnquoted() {
        assertThat(escaper.escape("WCAG 1.1.1")).isEqualTo("WCAG 1.1.1");
    }
}
