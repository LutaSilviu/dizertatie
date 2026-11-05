package usv.dizertatie.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.dataset.DatasetSeeder;
import usv.dizertatie.domain.BenchmarkPage;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.BenchmarkRunRepository;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BenchmarkRunnerServiceTest {

    @Mock
    private BenchmarkRunRepository benchmarkRunRepository;

    @Mock
    private BenchmarkPageRepository benchmarkPageRepository;

    @Mock
    private GroundTruthLabelRepository groundTruthLabelRepository;

    @Mock
    private BenchmarkRunExecutor benchmarkRunExecutor;

    private BenchmarkRunnerService service;

    @BeforeEach
    void setUp() {
        service = new BenchmarkRunnerService(
                benchmarkRunRepository,
                benchmarkPageRepository,
                groundTruthLabelRepository,
                benchmarkRunExecutor
        );
    }

    @Test
    void createRun_withExplicitUrls_createsRunWithCorrectTotalPages() {
        List<String> urls = List.of("https://example.com", "https://test.com");

        BenchmarkRun savedRun = BenchmarkRun.builder()
                .id(1L)
                .name("My Run")
                .status("PENDING")
                .totalPages(2)
                .pagesDone(0)
                .build();

        when(benchmarkRunRepository.save(any(BenchmarkRun.class))).thenReturn(savedRun);
        when(benchmarkPageRepository.save(any(BenchmarkPage.class))).thenAnswer(inv -> inv.getArgument(0));

        BenchmarkRun result = service.createRun("My Run", urls, false, AiModel.GPT4O_MINI);

        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(benchmarkRunRepository, atLeastOnce()).save(any(BenchmarkRun.class));
        verify(benchmarkPageRepository, times(2)).save(any(BenchmarkPage.class));
        verifyNoInteractions(groundTruthLabelRepository);
        // The scans run on the executor's background thread, not inside createRun.
        verify(benchmarkRunExecutor).execute(1L, urls, AiModel.GPT4O_MINI, BenchmarkMethods.ALL);
    }

    @Test
    void createRun_withNoUrlsAndNoBuiltinSet_isRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.createRun("Empty", List.of(), false, AiModel.GPT4O_MINI))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one URL");
        verifyNoInteractions(benchmarkRunRepository, benchmarkRunExecutor);
    }

    @Test
    void createRun_withUseBuiltinSet_loadsUrlsFromGroundTruthRepo() {
        GroundTruthLabel label1 = GroundTruthLabel.builder().id(1L).url("https://a.com").build();
        GroundTruthLabel label2 = GroundTruthLabel.builder().id(2L).url("https://b.com").build();
        // duplicate url to test distinct
        GroundTruthLabel label3 = GroundTruthLabel.builder().id(3L).url("https://a.com").build();

        when(groundTruthLabelRepository.findAll()).thenReturn(List.of(label1, label2, label3));

        BenchmarkRun savedRun = BenchmarkRun.builder()
                .id(2L)
                .name("Builtin Run")
                .status("PENDING")
                .totalPages(2)
                .pagesDone(0)
                .build();

        when(benchmarkRunRepository.save(any(BenchmarkRun.class))).thenReturn(savedRun);
        when(benchmarkPageRepository.save(any(BenchmarkPage.class))).thenAnswer(inv -> inv.getArgument(0));

        BenchmarkRun result = service.createRun("Builtin Run", List.of(), true, AiModel.GPT4O_MINI);

        // 3 labels but only 2 distinct URLs
        assertThat(result.getTotalPages()).isEqualTo(2);
        verify(groundTruthLabelRepository).findAll();
        // one page per distinct URL, plus the unlabelled negative-example page
        verify(benchmarkPageRepository, times(2 + DatasetSeeder.NEGATIVE_EXAMPLE_URLS.size()))
                .save(any(BenchmarkPage.class));
    }

    @Test
    void builtinUrls_includeNegativeExamplePagesThatHaveNoLabels() {
        GroundTruthLabel label = GroundTruthLabel.builder().url("https://a.com").build();
        when(groundTruthLabelRepository.findAll()).thenReturn(List.of(label, label));

        List<String> urls = service.builtinUrls();

        assertThat(DatasetSeeder.NEGATIVE_EXAMPLE_URLS)
                .containsExactly("http://localhost:8080/fixtures/wai-bad/after/home.html");
        assertThat(urls).containsExactly("https://a.com", "http://localhost:8080/fixtures/wai-bad/after/home.html");
    }

    @Test
    void createRun_withSelectedMethods_persistsAndPassesThemInCanonicalOrder() {
        BenchmarkRun savedRun = BenchmarkRun.builder().id(3L).name("Axe only").status("PENDING")
                .totalPages(1).pagesDone(0).build();
        when(benchmarkRunRepository.save(any(BenchmarkRun.class))).thenReturn(savedRun);
        when(benchmarkPageRepository.save(any(BenchmarkPage.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createRun("Axe only", List.of("https://a.com"), false, AiModel.GPT4O_MINI, List.of("COMBINED", "AXE"));

        verify(benchmarkRunRepository).save(org.mockito.ArgumentMatchers.argThat(
                (BenchmarkRun r) -> "AXE,COMBINED".equals(r.getMethods())));
        verify(benchmarkRunExecutor).execute(3L, List.of("https://a.com"), AiModel.GPT4O_MINI, List.of("AXE", "COMBINED"));
    }
}
