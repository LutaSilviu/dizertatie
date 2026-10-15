package usv.dizertatie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.domain.BenchmarkPage;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.BenchmarkRunRepository;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates asynchronous benchmark runs: creates {@link BenchmarkRun} and
 * {@link BenchmarkPage} records, then processes each URL by running AXE, AI,
 * and COMBINED scans in sequence, updating progress after each page.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkRunnerService {

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkPageRepository benchmarkPageRepository;
    private final GroundTruthLabelRepository groundTruthLabelRepository;
    private final ScanService scanService;

    /**
     * Creates a new {@link BenchmarkRun}, persists one {@link BenchmarkPage} per URL,
     * then triggers {@link #startRun} asynchronously.
     *
     * @param name          human-readable name for this run
     * @param urls          explicit list of URLs (ignored when {@code useBuiltinSet=true})
     * @param useBuiltinSet when {@code true}, URLs are loaded from all seeded {@link usv.dizertatie.domain.GroundTruthLabel} entries
     * @param model         the AI model to use for AI and COMBINED scans
     * @return the newly saved {@link BenchmarkRun} (status=PENDING)
     */
    public BenchmarkRun createRun(String name, List<String> urls, boolean useBuiltinSet, AiModel model) {
        List<String> resolvedUrls = useBuiltinSet
                ? groundTruthLabelRepository.findAll().stream()
                        .map(GroundTruthLabel::getUrl)
                        .distinct()
                        .collect(Collectors.toList())
                : urls;

        BenchmarkRun run = BenchmarkRun.builder()
                .name(name)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .totalPages(resolvedUrls.size())
                .pagesDone(0)
                .build();
        run = benchmarkRunRepository.save(run);

        for (String url : resolvedUrls) {
            BenchmarkPage page = BenchmarkPage.builder()
                    .run(run)
                    .url(url)
                    .status("PENDING")
                    .build();
            benchmarkPageRepository.save(page);
        }

        startRun(run.getId(), resolvedUrls, model);
        return run;
    }

    /**
     * Runs all three scan conditions (AXE, AI, COMBINED) for each URL sequentially.
     * <p>
     * Each page's result is stored immediately; a single page failure does not abort the run.
     * After all pages are processed the run status is set to COMPLETE, or FAILED when every
     * page failed.
     *
     * @param runId  the id of the persisted {@link BenchmarkRun}
     * @param urls   list of URLs to scan
     * @param model  the AI model to use for AI and COMBINED scans
     */
    @Async
    public void startRun(Long runId, List<String> urls, AiModel model) {
        BenchmarkRun run = benchmarkRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("BenchmarkRun not found: " + runId));
        run.setStatus("RUNNING");
        run = benchmarkRunRepository.save(run);

        int failedPages = 0;

        for (String url : urls) {
            List<BenchmarkPage> pages = benchmarkPageRepository.findByRunId(run.getId());
            BenchmarkPage page = pages.stream()
                    .filter(p -> url.equals(p.getUrl()) && "PENDING".equals(p.getStatus()))
                    .findFirst()
                    .orElseGet(() -> {
                        BenchmarkPage newPage = BenchmarkPage.builder()
                                .run(run)
                                .url(url)
                                .status("PENDING")
                                .build();
                        return benchmarkPageRepository.save(newPage);
                    });

            try {
                var axeScan = scanService.runAxeScan(url);
                page.setAxeScanId(axeScan.getId());

                var aiScan = scanService.runAiScan(url, model);
                page.setAiScanId(aiScan.getId());

                var combinedScan = scanService.runCombinedScan(url, model);
                page.setCombinedScanId(combinedScan.getId());

                page.setStatus("DONE");
            } catch (Exception e) {
                log.error("Failed to process page url={} in run id={}: {}", url, runId, e.getMessage(), e);
                page.setStatus("FAILED");
                failedPages++;
            }

            benchmarkPageRepository.save(page);

            run.setPagesDone(run.getPagesDone() + 1);
            run = benchmarkRunRepository.save(run);
        }

        if (failedPages == urls.size()) {
            run.setStatus("FAILED");
        } else {
            run.setStatus("COMPLETE");
        }
        benchmarkRunRepository.save(run);
    }
}
