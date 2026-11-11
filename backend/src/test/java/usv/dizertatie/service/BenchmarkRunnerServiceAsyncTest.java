package usv.dizertatie.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.domain.BenchmarkPage;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.BenchmarkRunRepository;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Checks that {@link BenchmarkRunnerService#createRun} goes through the Spring proxy, so the scans
 * run on the async executor instead of the request thread. Uses a slim context rather than
 * {@code @SpringBootTest} so a real {@code @Async} proxy is built without Playwright or a database.
 */
@SpringJUnitConfig(BenchmarkRunnerServiceAsyncTest.Config.class)
class BenchmarkRunnerServiceAsyncTest {

    @Configuration
    @EnableAsync
    @Import({BenchmarkRunnerService.class, BenchmarkRunExecutor.class})
    static class Config {

        @Bean
        BenchmarkRunRepository benchmarkRunRepository() {
            return mock(BenchmarkRunRepository.class);
        }

        @Bean
        BenchmarkPageRepository benchmarkPageRepository() {
            return mock(BenchmarkPageRepository.class);
        }

        @Bean
        GroundTruthLabelRepository groundTruthLabelRepository() {
            return mock(GroundTruthLabelRepository.class);
        }

        @Bean
        ScanService scanService() {
            return mock(ScanService.class);
        }
    }

    @Autowired
    private BenchmarkRunnerService service;

    @Autowired
    private BenchmarkRunRepository benchmarkRunRepository;

    @Autowired
    private BenchmarkPageRepository benchmarkPageRepository;

    @Autowired
    private ScanService scanService;

    private final CountDownLatch releaseScan = new CountDownLatch(1);

    @AfterEach
    void tearDown() {
        releaseScan.countDown();
    }

    @Test
    void createRun_returnsBeforeScansFinish_andScansRunOnAnotherThread() throws InterruptedException {
        BenchmarkRun savedRun = BenchmarkRun.builder()
                .id(1L)
                .name("Async Run")
                .status("PENDING")
                .totalPages(1)
                .pagesDone(0)
                .build();
        when(benchmarkRunRepository.save(any(BenchmarkRun.class))).thenReturn(savedRun);
        when(benchmarkRunRepository.findById(anyLong())).thenReturn(Optional.of(savedRun));
        when(benchmarkPageRepository.save(any(BenchmarkPage.class))).thenAnswer(inv -> inv.getArgument(0));

        CountDownLatch scanStarted = new CountDownLatch(1);
        AtomicReference<Thread> scanThread = new AtomicReference<>();
        when(scanService.runAxeScan(anyString())).thenAnswer(inv -> {
            scanThread.set(Thread.currentThread());
            scanStarted.countDown();
            releaseScan.await(10, TimeUnit.SECONDS);
            throw new IllegalStateException("scan released by test");
        });

        // A synchronous run would block here until releaseScan opens (10 s), failing the timeout.
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        BenchmarkRun result = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            callerThread.set(Thread.currentThread());
            return service.createRun("Async Run", List.of("https://x.test/"), false, AiModel.GPT4O_MINI);
        });

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(scanStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(scanThread.get()).isNotSameAs(callerThread.get());
    }
}
