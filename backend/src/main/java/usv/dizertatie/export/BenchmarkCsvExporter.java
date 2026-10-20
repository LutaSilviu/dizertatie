package usv.dizertatie.export;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import usv.dizertatie.domain.BenchmarkPage;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.FindingRepository;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Loads benchmark results and serializes them in the benchmark CSV format. */
@Component
@RequiredArgsConstructor
public class BenchmarkCsvExporter {

    private static final String HEADER =
            "run_id,page_url,method,wcag_criterion,wcag_level,severity,element_selector,description,ground_truth_match";
    private static final Comparator<String> TEXT_ORDER = Comparator.nullsFirst(String::compareTo);
    private static final Comparator<BenchmarkPage> PAGE_ORDER =
            Comparator.comparing(BenchmarkPage::getId, Comparator.nullsLast(Long::compareTo));
    private static final Comparator<Finding> FINDING_ORDER = Comparator
            .comparing(Finding::getWcagCriterion, TEXT_ORDER)
            .thenComparing(Finding::getElementSelector, TEXT_ORDER)
            .thenComparing(Finding::getWcagLevel, TEXT_ORDER)
            .thenComparing(Finding::getSeverity, TEXT_ORDER)
            .thenComparing(Finding::getDescription, TEXT_ORDER);

    private final BenchmarkPageRepository benchmarkPageRepository;
    private final FindingRepository findingRepository;
    private final GroundTruthLabelRepository groundTruthLabelRepository;
    private final CsvFieldEscaper csvFieldEscaper;

    public String export(Long runId) {
        StringBuilder csv = new StringBuilder(HEADER);
        benchmarkPageRepository.findByRunId(runId).stream()
                .sorted(PAGE_ORDER)
                .forEach(page -> appendPage(csv, runId, page));
        return csv.toString();
    }

    private void appendPage(StringBuilder csv, Long runId, BenchmarkPage page) {
        Set<String> labelKeys = groundTruthLabelRepository.findByUrl(page.getUrl()).stream()
                .map(label -> labelKey(label.getWcagCriterion(), label.getElementSelector()))
                .collect(Collectors.toSet());

        Stream.of(
                        new MethodScan("AXE", page.getAxeScanId()),
                        new MethodScan("AI", page.getAiScanId()),
                        new MethodScan("COMBINED", page.getCombinedScanId()))
                .filter(methodScan -> methodScan.scanId() != null)
                .forEach(methodScan -> appendFindings(csv, runId, page.getUrl(), methodScan, labelKeys));
    }

    private void appendFindings(StringBuilder csv, Long runId, String pageUrl,
                                MethodScan methodScan, Set<String> labelKeys) {
        findingRepository.findByScanId(methodScan.scanId()).stream()
                .sorted(FINDING_ORDER)
                .forEach(finding -> appendFinding(csv, runId, pageUrl, methodScan.method(), finding, labelKeys));
    }

    private void appendFinding(StringBuilder csv, Long runId, String pageUrl, String method,
                               Finding finding, Set<String> labelKeys) {
        boolean match = labelKeys.contains(labelKey(finding.getWcagCriterion(), finding.getElementSelector()));
        csv.append('\n')
                .append(csvFieldEscaper.escape(runId == null ? "" : runId.toString())).append(',')
                .append(csvFieldEscaper.escape(pageUrl)).append(',')
                .append(csvFieldEscaper.escape(method)).append(',')
                .append(csvFieldEscaper.escape(finding.getWcagCriterion())).append(',')
                .append(csvFieldEscaper.escape(finding.getWcagLevel())).append(',')
                .append(csvFieldEscaper.escape(finding.getSeverity())).append(',')
                .append(csvFieldEscaper.escape(finding.getElementSelector())).append(',')
                .append(csvFieldEscaper.escape(finding.getDescription())).append(',')
                .append(match);
    }

    private static String labelKey(String wcagCriterion, String elementSelector) {
        return (wcagCriterion == null ? "" : wcagCriterion)
                + "|"
                + (elementSelector == null ? "" : elementSelector);
    }

    private record MethodScan(String method, Long scanId) {
    }
}
