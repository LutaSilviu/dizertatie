package usv.dizertatie.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.repository.BenchmarkRunRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BenchmarkRunnerService {

    private final BenchmarkRunRepository benchmarkRunRepository;

    public BenchmarkRun createRun(String name, List<String> urls, boolean useBuiltinSet, AiModel model) {
        int totalPages = (urls != null) ? urls.size() : 0;

        BenchmarkRun run = BenchmarkRun.builder()
                .name(name)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .totalPages(totalPages)
                .pagesDone(0)
                .build();

        return benchmarkRunRepository.save(run);
    }

    public Optional<BenchmarkRun> findById(Long id) {
        return benchmarkRunRepository.findById(id);
    }

    public List<BenchmarkRun> findAllNewestFirst() {
        return benchmarkRunRepository.findAllByOrderByCreatedAtDesc();
    }
}
