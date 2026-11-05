package usv.dizertatie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.dataset.DatasetSeeder;
import usv.dizertatie.domain.BenchmarkPage;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.BenchmarkRunRepository;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates benchmark runs: persists a {@link BenchmarkRun} and its {@link BenchmarkPage} records,
 * then starts {@link BenchmarkRunExecutor} in the background to scan them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkRunnerService {

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkPageRepository benchmarkPageRepository;
    private final GroundTruthLabelRepository groundTruthLabelRepository;
    private final BenchmarkRunExecutor benchmarkRunExecutor;

    /**
     * Creates a new {@link BenchmarkRun}, persists one {@link BenchmarkPage} per URL,
     * then hands the run to {@link BenchmarkRunExecutor#execute} on a background thread.
     *
     * <p>Not {@code @Transactional} on purpose: every save commits immediately, so the background
     * thread can load the run. Should a caller wrap this in a transaction anyway, execution is
     * deferred until that transaction commits.
     *
     * @param name          human-readable name for this run
     * @param urls          explicit list of URLs (ignored when {@code useBuiltinSet=true})
     * @param useBuiltinSet when {@code true}, URLs are {@link #builtinUrls()} (labelled pages plus negative examples)
     * @param model         the AI model to use for AI and COMBINED scans
     * @param methods       scan methods to run, canonical names (see {@link BenchmarkMethods#parse})
     * @return the newly saved {@link BenchmarkRun} (status=PENDING)
     */
    public BenchmarkRun createRun(String name, List<String> urls, boolean useBuiltinSet, AiModel model,
                                  List<String> methods) {
        List<String> runMethods = BenchmarkMethods.parse(methods);
        List<String> resolvedUrls = useBuiltinSet ? builtinUrls() : urls;
        if (resolvedUrls == null || resolvedUrls.isEmpty()) {
            throw new IllegalArgumentException("A benchmark run needs at least one URL (or useBuiltinSet=true)");
        }

        BenchmarkRun run = BenchmarkRun.builder()
                .name(name)
                .status("PENDING")
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .totalPages(resolvedUrls.size())
                .pagesDone(0)
                .methods(BenchmarkMethods.join(runMethods))
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

        // Called through the executor bean's proxy so @Async applies; a self-call would run the
        // whole benchmark (minutes of browser + OpenAI work) on the HTTP request thread.
        Long runId = run.getId();
        List<String> runUrls = List.copyOf(resolvedUrls);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    benchmarkRunExecutor.execute(runId, runUrls, model, runMethods);
                }
            });
        } else {
            benchmarkRunExecutor.execute(runId, runUrls, model, runMethods);
        }
        return run;
    }

    /** Runs all three methods. */
    public BenchmarkRun createRun(String name, List<String> urls, boolean useBuiltinSet, AiModel model) {
        return createRun(name, urls, useBuiltinSet, model, BenchmarkMethods.ALL);
    }

    /**
     * The built-in benchmark pages: every page with ground-truth labels, plus the negative-example
     * pages, which have no labels (no expected violations) and so cannot be derived from them.
     */
    List<String> builtinUrls() {
        return Stream.concat(
                        groundTruthLabelRepository.findAll().stream().map(GroundTruthLabel::getUrl),
                        DatasetSeeder.NEGATIVE_EXAMPLE_URLS.stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public Optional<BenchmarkRun> findById(Long id) {
        return benchmarkRunRepository.findById(id);
    }

    public List<BenchmarkRun> findAllNewestFirst() {
        return benchmarkRunRepository.findAllByOrderByCreatedAtDesc();
    }
}
