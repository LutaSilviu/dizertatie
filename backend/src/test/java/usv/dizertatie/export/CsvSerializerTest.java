package usv.dizertatie.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.Scan;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvSerializerTest {

    private static final String HEADER =
            "scanId,url,method,timestamp,sourceMethod,wcagCriterion,wcagLevel,severity,elementSelector,description";

    private CsvSerializer serializer;
    private Scan scan;

    @BeforeEach
    void setUp() {
        serializer = new CsvSerializer(new CsvFieldEscaper());
        scan = Scan.builder()
                .id(1L)
                .url("https://example.com")
                .method("AI")
                .timestamp(LocalDateTime.of(2024, 1, 15, 10, 30, 0))
                .build();
    }

    @Test
    void emptyFindings_returnsHeaderOnly() {
        String result = serializer.serialize(scan, List.of());
        assertThat(result).isEqualTo(HEADER);
    }

    @Test
    void oneFinding_returnsHeaderPlusOneRow() {
        Finding finding = Finding.builder()
                .sourceMethod("axe")
                .wcagCriterion("1.1.1")
                .wcagLevel("A")
                .severity("serious")
                .elementSelector("img")
                .description("Image missing alt text")
                .build();

        String result = serializer.serialize(scan, List.of(finding));
        String[] lines = result.split("\n", -1);

        assertThat(lines).hasSize(2);
        assertThat(lines[0]).isEqualTo(HEADER);
        assertThat(lines[1]).isEqualTo(
                "1,https://example.com,AI,2024-01-15T10:30,axe,1.1.1,A,serious,img,Image missing alt text");
    }

    @Test
    void findingWithCommaInDescription_fieldIsQuoted() {
        Finding finding = Finding.builder()
                .sourceMethod("axe")
                .wcagCriterion("1.1.1")
                .wcagLevel("A")
                .severity("serious")
                .elementSelector("img")
                .description("Missing alt, aria-label, or title")
                .build();

        String result = serializer.serialize(scan, List.of(finding));
        String[] lines = result.split("\n", -1);

        assertThat(lines).hasSize(2);
        assertThat(lines[1]).endsWith("\"Missing alt, aria-label, or title\"");
    }

    @Test
    void findingWithQuoteInDescription_quoteIsDoubled() {
        Finding finding = Finding.builder()
                .sourceMethod("axe")
                .wcagCriterion("1.1.1")
                .wcagLevel("A")
                .severity("serious")
                .elementSelector("img")
                .description("Element has attribute \"role\" set incorrectly")
                .build();

        String result = serializer.serialize(scan, List.of(finding));
        String[] lines = result.split("\n", -1);

        assertThat(lines).hasSize(2);
        assertThat(lines[1]).endsWith("\"Element has attribute \"\"role\"\" set incorrectly\"");
    }
}
