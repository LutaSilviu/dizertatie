package usv.dizertatie.export;

import org.junit.jupiter.api.Test;
import usv.dizertatie.domain.BenchmarkPage;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.FindingRepository;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BenchmarkCsvExporterTest {

    @Test
    void export_ordersPagesMethodsAndFindingsAndMarksGroundTruthMatches() {
        BenchmarkPage firstPage = BenchmarkPage.builder()
                .id(1L)
                .url("https://first.example")
                .axeScanId(101L)
                .aiScanId(102L)
                .combinedScanId(103L)
                .build();
        BenchmarkPage secondPage = BenchmarkPage.builder()
                .id(2L)
                .url("https://second.example")
                .axeScanId(201L)
                .build();

        Finding axeFinding = finding("2.4.1", "a", "Link purpose");
        Finding aiEarlyFinding = finding("1.1.1", "img", "Missing alt text");
        Finding aiLateFinding = finding("2.4.1", "a", "Link purpose");
        Finding combinedFinding = finding("1.1.1", "img", "Missing alt text");
        Finding secondPageFinding = finding("3.1.1", "main", "Language missing");

        BenchmarkPageRepository pages = mock(BenchmarkPageRepository.class);
        FindingRepository findings = mock(FindingRepository.class);
        GroundTruthLabelRepository labels = mock(GroundTruthLabelRepository.class);
        when(pages.findByRunId(7L)).thenReturn(List.of(secondPage, firstPage));
        when(labels.findByUrl("https://first.example")).thenReturn(List.of(
                GroundTruthLabel.builder().wcagCriterion("1.1.1").elementSelector("img").build()));
        when(labels.findByUrl("https://second.example")).thenReturn(List.of());
        when(findings.findByScanId(101L)).thenReturn(List.of(axeFinding));
        when(findings.findByScanId(102L)).thenReturn(List.of(aiLateFinding, aiEarlyFinding));
        when(findings.findByScanId(103L)).thenReturn(List.of(combinedFinding));
        when(findings.findByScanId(201L)).thenReturn(List.of(secondPageFinding));

        BenchmarkCsvExporter exporter = new BenchmarkCsvExporter(
                pages, findings, labels, new CsvFieldEscaper());

        String csv = exporter.export(7L);

        assertThat(csv).isEqualTo("""
                run_id,page_url,method,wcag_criterion,wcag_level,severity,element_selector,description,ground_truth_match
                7,https://first.example,AXE,2.4.1,,,a,Link purpose,false
                7,https://first.example,AI,1.1.1,,,img,Missing alt text,true
                7,https://first.example,AI,2.4.1,,,a,Link purpose,false
                7,https://first.example,COMBINED,1.1.1,,,img,Missing alt text,true
                7,https://second.example,AXE,3.1.1,,,main,Language missing,false""");
    }

    @Test
    void export_withoutFindings_returnsHeaderOnly() {
        BenchmarkPageRepository pages = mock(BenchmarkPageRepository.class);
        FindingRepository findings = mock(FindingRepository.class);
        GroundTruthLabelRepository labels = mock(GroundTruthLabelRepository.class);
        when(pages.findByRunId(7L)).thenReturn(List.of());

        BenchmarkCsvExporter exporter = new BenchmarkCsvExporter(
                pages, findings, labels, new CsvFieldEscaper());

        assertThat(exporter.export(7L)).isEqualTo(
                "run_id,page_url,method,wcag_criterion,wcag_level,severity,element_selector,description,ground_truth_match");
    }

    private static Finding finding(String criterion, String selector, String description) {
        return Finding.builder()
                .wcagCriterion(criterion)
                .elementSelector(selector)
                .description(description)
                .build();
    }
}
