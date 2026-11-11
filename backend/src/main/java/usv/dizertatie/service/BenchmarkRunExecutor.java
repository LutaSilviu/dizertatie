package usv.dizertatie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.ai.OpenAiCostRates;
import usv.dizertatie.domain.BenchmarkPage;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.BenchmarkRunRepository;

import java.util.List;

/**
 * Executes a persisted {@link BenchmarkRun} in the background, running AXE, AI and COMBINED scans
 * for each URL and updating progress after each page.
 * <p>
 * Lives in its own bean so that {@link BenchmarkRunnerService#createRun} calls {@link #execute}
 * through the Spring proxy and {@code @Async} applies; a call on {@code this} would bypass it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkRunExecutor {

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkPageRepository benchmarkPageRepository;
    private final ScanService scanService;

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
    public void execute(Long runId, List<String> urls, AiModel model) {
        BenchmarkRun run = benchmarkRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("BenchmarkRun not found: " + runId));
        run.setStatus("RUNNING");
        run = benchmarkRunRepository.save(run);
        final BenchmarkRun savedRun = run;

        int failedPages = 0;

        for (String url : urls) {
            final BenchmarkRun currentRun = run;
            List<BenchmarkPage> pages = benchmarkPageRepository.findByRunId(savedRun.getId());
            BenchmarkPage page = pages.stream()
                    .filter(p -> url.equals(p.getUrl()) && "PENDING".equals(p.getStatus()))
                    .findFirst()
                    .orElseGet(() -> {
                        BenchmarkPage newPage = BenchmarkPage.builder()
                                .run(currentRun)
                                .url(url)
                                .status("PENDING")
                                .build();
                        return benchmarkPageRepository.save(newPage);
                    });

            try {
                // --- AXE scan ---
                long axeStart = System.currentTimeMillis();
                var axeScan = scanService.runAxeScan(url);
                page.setLatencyAxeMs(System.currentTimeMillis() - axeStart);
                page.setAxeScanId(axeScan.getId());

                // --- AI scan ---
                // Cost estimate: promptTokens ≈ htmlLength/4 (heuristic), completionTokens ≈ 500 (heuristic).
                // The ScanService does not return the HTML used; we use a fixed representative HTML size of
                // 15,000 characters (~3,750 tokens), which is a reasonable median for rendered web pages.
                // This is an estimate only — see OpenAiCostRates for details.
                final int ESTIMATED_HTML_LENGTH = 15_000;
                long aiStart = System.currentTimeMillis();
                var aiScan = scanService.runAiScan(url, model);
                page.setLatencyAiMs(System.currentTimeMillis() - aiStart);
                page.setAiScanId(aiScan.getId());
                double aiCost = OpenAiCostRates.estimateCost(model, ESTIMATED_HTML_LENGTH);

                // --- COMBINED scan ---
                long combinedStart = System.currentTimeMillis();
                var combinedScan = scanService.runCombinedScan(url, model);
                page.setLatencyCombinedMs(System.currentTimeMillis() - combinedStart);
                page.setCombinedScanId(combinedScan.getId());
                double combinedCost = OpenAiCostRates.estimateCost(model, ESTIMATED_HTML_LENGTH);

                // Sum AI + COMBINED costs (AXE-only has no OpenAI cost)
                page.setEstimatedCostUsd(aiCost + combinedCost);

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
