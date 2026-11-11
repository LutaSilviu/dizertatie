package usv.dizertatie.service;

import lombok.RequiredArgsConstructor;
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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Creates benchmark runs: persists the {@link BenchmarkRun} and one {@link BenchmarkPage}
 * per URL, then hands the run to {@link BenchmarkRunExecutor}, which scans asynchronously.
 */
@Service
@RequiredArgsConstructor
public class BenchmarkRunnerService {

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkPageRepository benchmarkPageRepository;
    private final GroundTruthLabelRepository groundTruthLabelRepository;
    private final BenchmarkRunExecutor benchmarkRunExecutor;

    /**
     * Creates a new {@link BenchmarkRun}, persists one {@link BenchmarkPage} per URL,
     * then hands the run to {@link BenchmarkRunExecutor#execute}, which runs asynchronously.
     * <p>
     * Not {@code @Transactional} on purpose: each save commits before the hand-off, so the async
     * run can read the run and its pages. Inside a transaction it could start before the commit.
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

        benchmarkRunExecutor.execute(run.getId(), resolvedUrls, model);
        return run;
    }

    public Optional<BenchmarkRun> findById(Long id) {
        return benchmarkRunRepository.findById(id);
    }

    public List<BenchmarkRun> findAllNewestFirst() {
        return benchmarkRunRepository.findAllByOrderByCreatedAtDesc();
    }
}
