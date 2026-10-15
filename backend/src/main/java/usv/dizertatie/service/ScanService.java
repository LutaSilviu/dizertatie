package usv.dizertatie.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import usv.dizertatie.ai.AiAnalysisService;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.ai.AiResponseParser;
import usv.dizertatie.ai.PromptLoader;
import usv.dizertatie.axe.AxeResultParser;
import usv.dizertatie.axe.AxeService;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.Scan;
import usv.dizertatie.playwright.PageCaptureService;
import usv.dizertatie.repository.FindingRepository;
import usv.dizertatie.repository.ScanRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestrates an Axe accessibility scan: captures the page, runs axe-core,
 * parses violations, and persists the resulting {@link Scan} and {@link Finding} entities.
 */
@Service
@RequiredArgsConstructor
public class ScanService {

    private final BrowserType browserType;
    private final AxeService axeService;
    private final AxeResultParser axeResultParser;
    private final ScanRepository scanRepository;
    private final FindingRepository findingRepository;
    private final PageCaptureService pageCaptureService;
    private final AiAnalysisService aiAnalysisService;
    private final AiResponseParser aiResponseParser;
    private final PromptLoader promptLoader;

    /**
     * Runs a full Axe scan for the given URL:
     * <ol>
     *   <li>Launches a headless browser and navigates to the URL</li>
     *   <li>Captures the page HTML and screenshot</li>
     *   <li>Runs axe-core to get violations JSON</li>
     *   <li>Parses violations into {@link Finding} entities</li>
     *   <li>Persists a {@link Scan} and all findings</li>
     * </ol>
     *
     * @param url the URL to scan
     * @return the persisted {@link Scan} entity
     */
    @Transactional
    public Scan runAxeScan(String url) {
        String violationsJson;
        String browserVersion;

        Browser browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(true));
        Page page = null;
        try {
            page = browser.newPage();
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));

            browserVersion = browser.browserType().name() + "/" + browser.version();

            // Run axe-core on the already-loaded page
            violationsJson = axeService.run(page);
        } finally {
            if (page != null) {
                page.close();
            }
            browser.close();
        }

        List<Finding> findings = axeResultParser.parse(violationsJson);

        Scan scan = Scan.builder()
                .url(url)
                .method("AXE")
                .axeVersion(AxeService.AXE_VERSION)
                .browserVersion(browserVersion)
                .timestamp(LocalDateTime.now())
                .build();
        scan = scanRepository.save(scan);

        final Scan savedScan = scan;
        findings.forEach(f -> f.setScan(savedScan));
        findingRepository.saveAll(findings);

        return savedScan;
    }

    /**
     * Runs an AI accessibility scan for the given URL using the specified model:
     * <ol>
     *   <li>Captures the rendered HTML via {@link PageCaptureService}</li>
     *   <li>Sends HTML to the AI model via {@link AiAnalysisService}</li>
     *   <li>Parses the raw JSON response into {@link Finding} entities</li>
     *   <li>Persists a {@link Scan} and all findings</li>
     * </ol>
     *
     * @param url   the URL to scan
     * @param model the AI model to use
     * @return the persisted {@link Scan} entity
     */
    @Transactional
    public Scan runAiScan(String url, AiModel model) {
        String html = pageCaptureService.captureHtml(url);
        String rawJson = aiAnalysisService.analyze(html, model);
        List<Finding> findings = aiResponseParser.parse(rawJson);

        Scan scan = Scan.builder()
                .url(url)
                .method("AI")
                .modelId(model.getModelId())
                .promptVersion(promptLoader.getVersion())
                .rawAiResponse(rawJson)
                .timestamp(LocalDateTime.now())
                .build();
        scan = scanRepository.save(scan);

        final Scan savedScan = scan;
        findings.forEach(f -> f.setScan(savedScan));
        findingRepository.saveAll(findings);

        return savedScan;
    }

    /**
     * Finds a Scan by ID, throwing 404 if not found.
     *
     * @param id the scan ID
     * @return the {@link Scan} entity
     */
    public Scan findById(Long id) {
        return scanRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Scan not found: " + id));
    }

    /**
     * Returns all findings for the given scan.
     *
     * @param scan the scan entity
     * @return list of {@link Finding} entities
     */
    public List<Finding> findingsForScan(Scan scan) {
        return findingRepository.findByScan(scan);
    }
}
