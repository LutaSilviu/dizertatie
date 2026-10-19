package usv.dizertatie.service;

import com.microsoft.playwright.BrowserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import usv.dizertatie.ai.AiAnalysisService;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.ai.AiResponseParser;
import usv.dizertatie.ai.PromptLoader;
import usv.dizertatie.axe.AxeResultParser;
import usv.dizertatie.axe.AxeService;
import usv.dizertatie.domain.Scan;
import usv.dizertatie.playwright.PageCapture;
import usv.dizertatie.playwright.PageCaptureService;
import usv.dizertatie.repository.FindingRepository;
import usv.dizertatie.repository.ScanRepository;
import usv.dizertatie.scoring.SeverityScoringService;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    private static final String URL = "https://example.com";
    private static final String HTML = "<html><body><img src='a.png'/></body></html>";
    private static final byte[] SCREENSHOT = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);

    @Mock private BrowserType browserType;
    @Mock private AxeService axeService;
    @Mock private AxeResultParser axeResultParser;
    @Mock private ScanRepository scanRepository;
    @Mock private FindingRepository findingRepository;
    @Mock private PageCaptureService pageCaptureService;
    @Mock private AiAnalysisService aiAnalysisService;
    @Mock private AiResponseParser aiResponseParser;
    @Mock private PromptLoader promptLoader;
    @Mock private SeverityScoringService severityScoringService;
    @Mock private FindingMergeService findingMergeService;

    @InjectMocks private ScanService scanService;

    @Test
    void runAiScan_passesCapturedScreenshotToAiAnalysis() {
        when(pageCaptureService.capture(URL)).thenReturn(new PageCapture(HTML, SCREENSHOT));
        when(aiAnalysisService.analyze(any(PageCapture.class), any(AiModel.class)))
                .thenReturn("{\"findings\":[]}");
        when(aiResponseParser.parse(anyString())).thenReturn(List.of());
        when(promptLoader.getVersion()).thenReturn("v1");
        when(scanRepository.save(any(Scan.class))).thenAnswer(inv -> inv.getArgument(0));

        scanService.runAiScan(URL, AiModel.GPT4O);

        ArgumentCaptor<PageCapture> captor = ArgumentCaptor.forClass(PageCapture.class);
        org.mockito.Mockito.verify(aiAnalysisService).analyze(captor.capture(), any(AiModel.class));

        assertThat(captor.getValue().html()).isEqualTo(HTML);
        assertThat(captor.getValue().screenshotPng())
                .as("the screenshot captured from the page must reach the AI analysis")
                .isEqualTo(SCREENSHOT);
    }
}
