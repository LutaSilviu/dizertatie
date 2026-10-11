package usv.dizertatie.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import usv.dizertatie.repository.FindingRepository;
import usv.dizertatie.repository.ScanRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ScanFindingRepositoryTest {

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private FindingRepository findingRepository;

    @Test
    void saveScanWithFindingAndRetrieve() {
        Scan scan = Scan.builder()
                .url("https://example.com")
                .method("AXE")
                .modelId("gpt-4o")
                .promptVersion("v1")
                .axeVersion("4.9")
                .browserVersion("Chrome/120")
                .cacheHit(false)
                .rawAiResponse(null)
                .timestamp(LocalDateTime.of(2026, 10, 11, 12, 0))
                .build();
        scan = scanRepository.save(scan);

        Finding finding = Finding.builder()
                .scan(scan)
                .sourceMethod("AXE")
                .wcagCriterion("1.1.1")
                .wcagLevel("A")
                .severity("critical")
                .elementSelector("img.hero")
                .description("Image missing alt text")
                .suggestion("Add meaningful alt attribute")
                .confidence(0.95)
                .build();
        findingRepository.save(finding);

        List<Finding> findings = findingRepository.findAll();
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getScan().getId()).isEqualTo(scan.getId());
        assertThat(findings.get(0).getWcagCriterion()).isEqualTo("1.1.1");

        Scan savedScan = scanRepository.findById(scan.getId()).orElseThrow();
        assertThat(savedScan.getUrl()).isEqualTo("https://example.com");
        assertThat(savedScan.getMethod()).isEqualTo("AXE");
    }
}
