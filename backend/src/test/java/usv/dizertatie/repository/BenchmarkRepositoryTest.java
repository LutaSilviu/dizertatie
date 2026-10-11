package usv.dizertatie.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import usv.dizertatie.domain.BenchmarkPage;
import usv.dizertatie.domain.BenchmarkRun;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BenchmarkRepositoryTest {

    @Autowired
    private BenchmarkRunRepository benchmarkRunRepository;

    @Autowired
    private BenchmarkPageRepository benchmarkPageRepository;

    @Test
    void findByRunId_returnsPagesForRun() {
        BenchmarkRun run = benchmarkRunRepository.save(BenchmarkRun.builder()
                .name("Test Run")
                .status("RUNNING")
                .createdAt(LocalDateTime.of(2026, 10, 11, 12, 0))
                .totalPages(2)
                .pagesDone(0)
                .build());

        benchmarkPageRepository.save(BenchmarkPage.builder()
                .run(run)
                .url("https://example.com/page1")
                .status("PENDING")
                .build());

        benchmarkPageRepository.save(BenchmarkPage.builder()
                .run(run)
                .url("https://example.com/page2")
                .status("PENDING")
                .build());

        List<BenchmarkPage> pages = benchmarkPageRepository.findByRunId(run.getId());

        assertThat(pages).hasSize(2);
        assertThat(pages).allMatch(p -> p.getRun().getId().equals(run.getId()));
    }

    @Test
    void countByRunIdAndStatus_returnsCorrectCount() {
        BenchmarkRun run = benchmarkRunRepository.save(BenchmarkRun.builder()
                .name("Count Run")
                .status("RUNNING")
                .createdAt(LocalDateTime.of(2026, 10, 11, 13, 0))
                .totalPages(3)
                .pagesDone(1)
                .build());

        benchmarkPageRepository.save(BenchmarkPage.builder()
                .run(run)
                .url("https://example.com/a")
                .status("PENDING")
                .build());

        benchmarkPageRepository.save(BenchmarkPage.builder()
                .run(run)
                .url("https://example.com/b")
                .status("PENDING")
                .build());

        benchmarkPageRepository.save(BenchmarkPage.builder()
                .run(run)
                .url("https://example.com/c")
                .status("DONE")
                .build());

        long pendingCount = benchmarkPageRepository.countByRunIdAndStatus(run.getId(), "PENDING");
        long doneCount = benchmarkPageRepository.countByRunIdAndStatus(run.getId(), "DONE");

        assertThat(pendingCount).isEqualTo(2);
        assertThat(doneCount).isEqualTo(1);
    }

    @Test
    void findAllByOrderByCreatedAtDesc_returnsRunsNewestFirst() {
        BenchmarkRun older = benchmarkRunRepository.save(BenchmarkRun.builder()
                .name("Older Run")
                .status("DONE")
                .createdAt(LocalDateTime.of(2026, 10, 10, 10, 0))
                .totalPages(1)
                .pagesDone(1)
                .build());

        BenchmarkRun newer = benchmarkRunRepository.save(BenchmarkRun.builder()
                .name("Newer Run")
                .status("DONE")
                .createdAt(LocalDateTime.of(2026, 10, 11, 10, 0))
                .totalPages(1)
                .pagesDone(1)
                .build());

        List<BenchmarkRun> runs = benchmarkRunRepository.findAllByOrderByCreatedAtDesc();

        assertThat(runs).hasSizeGreaterThanOrEqualTo(2);
        int newerIndex = -1;
        int olderIndex = -1;
        for (int i = 0; i < runs.size(); i++) {
            if (runs.get(i).getId().equals(newer.getId())) newerIndex = i;
            if (runs.get(i).getId().equals(older.getId())) olderIndex = i;
        }
        assertThat(newerIndex).isLessThan(olderIndex);
    }
}
