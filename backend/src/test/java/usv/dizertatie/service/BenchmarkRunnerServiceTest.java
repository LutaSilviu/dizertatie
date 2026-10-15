package usv.dizertatie.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import usv.dizertatie.ai.AiModel;
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
    private ScanService scanService;

    private BenchmarkRunnerService service;

    @BeforeEach
    void setUp() {
        service = new BenchmarkRunnerService(
                benchmarkRunRepository,
                benchmarkPageRepository,
                groundTruthLabelRepository,
                scanService
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
        // one page per distinct URL
        verify(benchmarkPageRepository, times(2)).save(any(BenchmarkPage.class));
    }
}
