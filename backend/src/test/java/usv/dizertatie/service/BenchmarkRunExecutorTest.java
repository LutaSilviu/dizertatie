package usv.dizertatie.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import usv.dizertatie.ai.AiAnalysisException;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.ai.AiStatusService;
import usv.dizertatie.domain.BenchmarkPage;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.domain.Scan;
import usv.dizertatie.error.FailureMessages;
import usv.dizertatie.playwright.PageCaptureException;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.BenchmarkRunRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BenchmarkRunExecutorTest {

    private static final String OK_URL = "https://ok.example";
    private static final String BAD_URL = "https://bad.example";

    @Mock private BenchmarkRunRepository runRepository;
    @Mock private BenchmarkPageRepository pageRepository;
    @Mock private ScanService scanService;
    @Mock private AiStatusService aiStatusService;

    private BenchmarkRunExecutor executor;
    private BenchmarkRun run;
    private final List<String> statusHistory = new ArrayList<>();
    private final List<BenchmarkPage> pages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        executor = new BenchmarkRunExecutor(runRepository, pageRepository, scanService, aiStatusService);
        run = BenchmarkRun.builder().id(7L).name("r").status("PENDING")
                .createdAt(LocalDateTime.now()).totalPages(2).pagesDone(0).build();
        when(aiStatusService.isKeyMissing()).thenReturn(false);
    }

    private void stubRunPersistence() {
        when(runRepository.findById(7L)).thenReturn(Optional.of(run));
        when(runRepository.save(any(BenchmarkRun.class))).thenAnswer(inv -> {
            BenchmarkRun r = inv.getArgument(0);
            statusHistory.add(r.getStatus());
            return r;
        });
    }

    private void stubPages(String... urls) {
        for (String url : urls) {
            pages.add(BenchmarkPage.builder().run(run).url(url).status("PENDING").build());
        }
        when(pageRepository.findByRunId(7L)).thenReturn(pages);
        when(pageRepository.save(any(BenchmarkPage.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private BenchmarkPage page(String url) {
        return pages.stream().filter(p -> p.getUrl().equals(url)).findFirst().orElseThrow();
    }

    private static Scan scan(long id) {
        return Scan.builder().id(id).build();
    }

    private static AiAnalysisException keyRejected() {
        return new AiAnalysisException("AI analysis failed for model gpt-4o-mini",
                new RuntimeException("401: Incorrect API key provided: sk-place***-key"));
    }

    @Test
    void execute_allMethodsSucceed_runCompleteWithoutError() {
        stubRunPersistence();
        stubPages(OK_URL);
        when(scanService.runAxeScan(OK_URL)).thenReturn(scan(1));
        when(scanService.runAiScan(eq(OK_URL), any())).thenReturn(scan(2));
        when(scanService.runCombinedScan(eq(OK_URL), any())).thenReturn(scan(3));

        executor.execute(7L, List.of(OK_URL), AiModel.GPT4O_MINI, BenchmarkMethods.ALL);

        assertThat(run.getStatus()).isEqualTo("COMPLETE");
        assertThat(run.getErrorSummary()).isNull();
        assertThat(statusHistory).first().isEqualTo("RUNNING");
        BenchmarkPage p = page(OK_URL);
        assertThat(p.getStatus()).isEqualTo("DONE");
        assertThat(p.getAxeScanId()).isEqualTo(1L);
        assertThat(p.getAiScanId()).isEqualTo(2L);
        assertThat(p.getCombinedScanId()).isEqualTo(3L);
        assertThat(p.getEstimatedCostUsd()).isPositive();
    }

    @Test
    void execute_aiKeyRejected_keepsAxeResults_skipsFurtherAiCalls_andSummarisesFailure() {
        stubRunPersistence();
        stubPages(OK_URL, BAD_URL);
        when(scanService.runAxeScan(anyString())).thenReturn(scan(1), scan(4));
        when(scanService.runAiScan(anyString(), any())).thenThrow(keyRejected());

        executor.execute(7L, List.of(OK_URL, BAD_URL), AiModel.GPT4O_MINI, BenchmarkMethods.ALL);

        assertThat(run.getStatus()).isEqualTo("COMPLETE");
        assertThat(run.getPagesDone()).isEqualTo(2);
        assertThat(page(OK_URL).getAxeScanId()).isEqualTo(1L);
        assertThat(page(BAD_URL).getAxeScanId()).isEqualTo(4L);
        assertThat(page(OK_URL).getStatus()).isEqualTo("DONE");
        assertThat(page(OK_URL).getAiScanId()).isNull();
        assertThat(page(OK_URL).getEstimatedCostUsd()).isNull();
        // After the first rejection neither AI nor COMBINED is attempted again.
        verify(scanService, times(1)).runAiScan(anyString(), any());
        verify(scanService, never()).runCombinedScan(anyString(), any());
        assertThat(run.getErrorSummary())
                .isEqualTo("AI failed on 2/2 pages: OpenAI rejected the API key. Set a valid OPENAI_API_KEY in .env "
                        + "and restart the backend; COMBINED failed on 2/2 pages: OpenAI rejected the API key. Set a "
                        + "valid OPENAI_API_KEY in .env and restart the backend")
                .doesNotContain("sk-");
    }

    @Test
    void execute_missingKey_doesNotAttemptAiMethods() {
        when(aiStatusService.isKeyMissing()).thenReturn(true);
        stubRunPersistence();
        stubPages(OK_URL);
        when(scanService.runAxeScan(OK_URL)).thenReturn(scan(1));

        executor.execute(7L, List.of(OK_URL), AiModel.GPT4O_MINI, List.of("AXE", "AI"));

        assertThat(run.getStatus()).isEqualTo("COMPLETE");
        verify(scanService, never()).runAiScan(anyString(), any());
        assertThat(run.getErrorSummary()).startsWith("AI failed on 1/1 page: No OpenAI API key is configured");
    }

    @Test
    void execute_onlyRequestedMethodsRun() {
        stubRunPersistence();
        stubPages(OK_URL);
        when(scanService.runAxeScan(OK_URL)).thenReturn(scan(1));

        executor.execute(7L, List.of(OK_URL), AiModel.GPT4O_MINI, List.of("AXE"));

        verify(scanService).runAxeScan(OK_URL);
        verifyNoMoreInteractions(scanService);
        verify(aiStatusService, never()).isKeyMissing();
        assertThat(run.getStatus()).isEqualTo("COMPLETE");
    }

    @Test
    void execute_pageLoadFails_otherPagesStillCount() {
        stubRunPersistence();
        stubPages(OK_URL, BAD_URL);
        when(scanService.runAxeScan(OK_URL)).thenReturn(scan(1));
        when(scanService.runAxeScan(BAD_URL)).thenThrow(new PageCaptureException(
                "Could not load https://bad.example: The domain name could not be resolved"));

        executor.execute(7L, List.of(OK_URL, BAD_URL), AiModel.GPT4O_MINI, List.of("AXE"));

        assertThat(run.getStatus()).isEqualTo("COMPLETE");
        assertThat(page(BAD_URL).getStatus()).isEqualTo("FAILED");
        assertThat(run.getErrorSummary()).isEqualTo(
                "AXE failed on 1/2 pages: Could not load https://bad.example: The domain name could not be resolved");
    }

    @Test
    void execute_nothingSucceeds_runIsFailed() {
        stubRunPersistence();
        stubPages(BAD_URL);
        when(scanService.runAxeScan(anyString())).thenThrow(new PageCaptureException("unreachable"));

        executor.execute(7L, List.of(BAD_URL), AiModel.GPT4O_MINI, List.of("AXE"));

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getPagesDone()).isEqualTo(1);
        assertThat(run.getErrorSummary()).isEqualTo("AXE failed on 1/1 page: unreachable");
    }

    @Test
    void execute_infrastructureFailure_marksRunFailedInsteadOfLeavingItRunning() {
        stubRunPersistence();
        when(pageRepository.findByRunId(7L)).thenThrow(new IllegalStateException("database down"));

        executor.execute(7L, List.of(OK_URL), AiModel.GPT4O_MINI, BenchmarkMethods.ALL);

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getErrorSummary()).contains("database down");
    }

    @Test
    void execute_unknownRun_doesNothing() {
        when(runRepository.findById(99L)).thenReturn(Optional.empty());

        executor.execute(99L, List.of(OK_URL), AiModel.GPT4O_MINI, BenchmarkMethods.ALL);

        verifyNoInteractions(scanService, pageRepository);
    }

    @Test
    void summarize_mentionsOtherDistinctErrors_andIsNullWithoutFailures() {
        Map<String, BenchmarkRunExecutor.MethodTally> tallies = new LinkedHashMap<>();
        BenchmarkRunExecutor.MethodTally axe = new BenchmarkRunExecutor.MethodTally();
        axe.succeeded = 3;
        tallies.put("AXE", axe);
        assertThat(BenchmarkRunExecutor.summarize(tallies, 3)).isNull();

        axe.fail("Timeout.");
        axe.fail("DNS");
        axe.fail("DNS");
        assertThat(BenchmarkRunExecutor.summarize(tallies, 6)).isEqualTo("AXE failed on 3/6 pages: Timeout (+1 other error)");
    }

    @Test
    void failRunsInterruptedByRestart_marksPendingAndRunningRunsFailed() {
        BenchmarkRun running = BenchmarkRun.builder().id(1L).status("RUNNING").build();
        BenchmarkRun pending = BenchmarkRun.builder().id(2L).status("PENDING").build();
        when(runRepository.findByStatusIn(List.of("PENDING", "RUNNING"))).thenReturn(List.of(running, pending));

        executor.failRunsInterruptedByRestart();

        assertThat(running.getStatus()).isEqualTo("FAILED");
        assertThat(pending.getStatus()).isEqualTo("FAILED");
        assertThat(running.getErrorSummary()).contains("restarted");
        verify(runRepository, times(2)).save(any(BenchmarkRun.class));
    }

    @Test
    void keyRejectedMessage_isTheCleanConstant() {
        assertThat(FailureMessages.describe(keyRejected())).isEqualTo(FailureMessages.OPENAI_KEY_REJECTED);
    }
}
