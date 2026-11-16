package usv.dizertatie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.ai.AiStatusService;
import usv.dizertatie.ai.OpenAiCostRates;
import usv.dizertatie.domain.BenchmarkPage;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.domain.Scan;
import usv.dizertatie.domain.ScanStatus;
import usv.dizertatie.error.FailureMessages;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.BenchmarkRunRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes a persisted {@link BenchmarkRun} in the background: for each URL it runs the requested
 * scan methods (AXE, AI, COMBINED) and records progress after each page.
 *
 * <p>Each method is isolated: one failing (typically AI, when OpenAI is unavailable) never discards
 * the results of the others, so AXE can still be benchmarked. Once OpenAI has refused the key, or no
 * key is configured, the AI-based methods are not attempted again for the rest of the run.
 *
 * <p>Lives in its own bean so that {@link #execute} is invoked through the Spring proxy and
 * {@code @Async} actually applies (see {@link BenchmarkRunnerService#createRun}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkRunExecutor {

    /**
     * Cost estimate input: promptTokens ≈ htmlLength/4 (heuristic), completionTokens ≈ 500 (heuristic).
     * The ScanService does not return the HTML used; a fixed representative HTML size of 15,000
     * characters (~3,750 tokens) is a reasonable median for rendered web pages.
     * This is an estimate only — see OpenAiCostRates for details.
     */
    static final int ESTIMATED_HTML_LENGTH = 15_000;

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkPageRepository benchmarkPageRepository;
    private final ScanService scanService;
    private final AiStatusService aiStatusService;
    private final usv.dizertatie.ai.AiResponseCache aiResponseCache;

    /** Per-method outcome counts and the distinct failure reasons, in first-seen order. */
    static final class MethodTally {
        int succeeded;
        int failed;
        final Set<String> reasons = new LinkedHashSet<>();

        void fail(String reason) {
            failed++;
            reasons.add(reason);
        }
    }

    /** Mutable state shared across the pages of one run. */
    private static final class RunContext {
        final Map<String, MethodTally> tallies = new LinkedHashMap<>();
        /** Non-null once AI cannot work for this run; the reason recorded for skipped AI scans. */
        String aiUnavailable;
        int pagesProcessed;
    }

    /**
     * Runs the requested scan methods for each URL sequentially.
     * <p>
     * Each page's results are stored immediately; failures are isolated per page and per method.
     * The run ends COMPLETE when at least one method produced a result on at least one page, and
     * FAILED only when nothing succeeded or the run itself could not proceed. The run always
     * reaches a final status, and {@code errorSummary} describes what failed.
     *
     * @param runId   the id of the persisted {@link BenchmarkRun}
     * @param urls    list of URLs to scan
     * @param model   the AI model to use for AI and COMBINED scans
     * @param methods canonical method names to run (see {@link BenchmarkMethods})
     */
    @Async
    public void execute(Long runId, List<String> urls, AiModel model, List<String> methods) {
        BenchmarkRun run = benchmarkRunRepository.findById(runId).orElse(null);
        if (run == null) {
            log.error("BenchmarkRun not found: {}", runId);
            return;
        }
        try {
            run.setStatus("RUNNING");
            run = benchmarkRunRepository.save(run);

            // Each run asks the model afresh, so repeated runs are independent samples. Within a
            // run, AI and COMBINED share one reply per page, so COMBINED is exactly AXE plus AI.
            aiResponseCache.clear();

            RunContext ctx = new RunContext();
            for (String m : methods) {
                ctx.tallies.put(m, new MethodTally());
            }
            if (methods.stream().anyMatch(BenchmarkMethods::needsAi) && aiStatusService.isKeyMissing()) {
                ctx.aiUnavailable = FailureMessages.OPENAI_KEY_MISSING;
            }

            for (String url : urls) {
                processPage(run, url, model, methods, ctx);
                ctx.pagesProcessed++;
                run.setPagesDone(run.getPagesDone() + 1);
                run.setErrorSummary(summarize(ctx));
                run = benchmarkRunRepository.save(run);
            }

            boolean anySuccess = ctx.tallies.values().stream().anyMatch(t -> t.succeeded > 0);
            run.setStatus(anySuccess ? "COMPLETE" : "FAILED");
            run.setErrorSummary(summarize(ctx));
            benchmarkRunRepository.save(run);
        } catch (RuntimeException e) {
            // Database or other infrastructure failure: never leave the run stuck in RUNNING.
            log.error("Benchmark run id={} aborted: {}", runId, e.getMessage(), e);
            markFailed(runId, "Benchmark run aborted: " + FailureMessages.describe(e));
        }
    }

    /** Scans one page with each requested method; one method failing does not affect the others. */
    private void processPage(BenchmarkRun run, String url, AiModel model, List<String> methods, RunContext ctx) {
        BenchmarkPage page = benchmarkPageRepository.findByRunId(run.getId()).stream()
                .filter(p -> url.equals(p.getUrl()) && "PENDING".equals(p.getStatus()))
                .findFirst()
                .orElseGet(() -> benchmarkPageRepository.save(BenchmarkPage.builder()
                        .run(run)
                        .url(url)
                        .status("PENDING")
                        .build()));

        double cost = 0.0;
        boolean anyAiSuccess = false;
        boolean anySuccess = false;
        for (String method : methods) {
            MethodTally tally = ctx.tallies.get(method);
            if (BenchmarkMethods.needsAi(method) && ctx.aiUnavailable != null) {
                tally.fail(ctx.aiUnavailable);
                continue;
            }
            try {
                aiResponseCache.takeReusedMillis();
                long start = System.currentTimeMillis();
                Scan scan = switch (method) {
                    case BenchmarkMethods.AXE -> scanService.runAxeScan(url);
                    case BenchmarkMethods.AI -> scanService.runAiScan(url, model);
                    case BenchmarkMethods.COMBINED -> scanService.runCombinedScan(url, model);
                    default -> throw new IllegalStateException("Unknown method " + method);
                };
                // A reply reused from the AI scan of the same page still counts: the latency
                // reported for COMBINED is what the method takes on its own.
                long latency = System.currentTimeMillis() - start + aiResponseCache.takeReusedMillis();
                // ScanService persists failed attempts instead of throwing. A scan that did not
                // complete has no evidence, so it must not be scored as "found nothing".
                if (scan.getStatus() != null && scan.getStatus() != ScanStatus.COMPLETED) {
                    String reason = scan.getFailureReason() == null || scan.getFailureReason().isBlank()
                            ? "Scan ended with status " + scan.getStatus()
                            : scan.getFailureReason();
                    log.warn("{} scan did not complete for url={} in run id={}: {}", method, url, run.getId(), reason);
                    // Keep the link to the failed attempt: it belongs to this run (not to the
                    // user's scan history) and MetricsService leaves non-completed scans out.
                    switch (method) {
                        case BenchmarkMethods.AXE -> page.setAxeScanId(scan.getId());
                        case BenchmarkMethods.AI -> page.setAiScanId(scan.getId());
                        default -> page.setCombinedScanId(scan.getId());
                    }
                    tally.fail(reason);
                    if (reason.contains(FailureMessages.OPENAI_KEY_REJECTED)) {
                        ctx.aiUnavailable = FailureMessages.OPENAI_KEY_REJECTED;
                    }
                    continue;
                }
                switch (method) {
                    case BenchmarkMethods.AXE -> {
                        page.setLatencyAxeMs(latency);
                        page.setAxeScanId(scan.getId());
                    }
                    case BenchmarkMethods.AI -> {
                        page.setLatencyAiMs(latency);
                        page.setAiScanId(scan.getId());
                    }
                    default -> {
                        page.setLatencyCombinedMs(latency);
                        page.setCombinedScanId(scan.getId());
                    }
                }
                if (BenchmarkMethods.needsAi(method)) {
                    cost += OpenAiCostRates.estimateCost(model, ESTIMATED_HTML_LENGTH);
                    anyAiSuccess = true;
                }
                tally.succeeded++;
                anySuccess = true;
            } catch (Exception e) {
                String reason = FailureMessages.describe(e);
                log.warn("{} scan failed for url={} in run id={}: {}", method, url, run.getId(), reason, e);
                tally.fail(reason);
                if (FailureMessages.isAiAuthFailure(e)) {
                    ctx.aiUnavailable = FailureMessages.OPENAI_KEY_REJECTED;
                }
            }
        }

        // AXE-only has no OpenAI cost
        page.setEstimatedCostUsd(anyAiSuccess ? cost : null);
        page.setStatus(anySuccess ? "DONE" : "FAILED");
        benchmarkPageRepository.save(page);
    }

    /** e.g. "AI failed on 2/2 pages: OpenAI rejected the API key. ..."; null when nothing failed. */
    static String summarize(Map<String, MethodTally> tallies, int pages) {
        List<String> parts = new ArrayList<>();
        tallies.forEach((method, t) -> {
            if (t.failed == 0) {
                return;
            }
            String first = t.reasons.iterator().next();
            int others = t.reasons.size() - 1;
            parts.add(method + " failed on " + t.failed + "/" + pages + (pages == 1 ? " page: " : " pages: ")
                    + stripFinalPeriod(first)
                    + (others > 0 ? " (+" + others + (others == 1 ? " other error)" : " other errors)") : ""));
        });
        return parts.isEmpty() ? null : String.join("; ", parts);
    }

    private static String summarize(RunContext ctx) {
        return summarize(ctx.tallies, ctx.pagesProcessed);
    }

    private static String stripFinalPeriod(String s) {
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    private void markFailed(Long runId, String reason) {
        try {
            benchmarkRunRepository.findById(runId).ifPresent(r -> {
                r.setStatus("FAILED");
                r.setErrorSummary(reason);
                benchmarkRunRepository.save(r);
            });
        } catch (RuntimeException e) {
            log.error("Could not mark benchmark run id={} as FAILED", runId, e);
        }
    }

    /**
     * Runs execute in memory, so a restart abandons any run that was PENDING or RUNNING. Mark
     * those FAILED at startup; otherwise clients would poll their status forever.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void failRunsInterruptedByRestart() {
        List<BenchmarkRun> interrupted = benchmarkRunRepository.findByStatusIn(List.of("PENDING", "RUNNING"));
        for (BenchmarkRun run : interrupted) {
            run.setStatus("FAILED");
            run.setErrorSummary("The backend restarted before this run finished.");
            benchmarkRunRepository.save(run);
        }
        if (!interrupted.isEmpty()) {
            log.warn("Marked {} benchmark run(s) interrupted by a restart as FAILED", interrupted.size());
        }
    }
}
