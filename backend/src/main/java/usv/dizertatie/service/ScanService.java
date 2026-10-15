package usv.dizertatie.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import usv.dizertatie.axe.AxeResultParser;
import usv.dizertatie.axe.AxeService;
import usv.dizertatie.controller.ScanHistoryResponse;
import usv.dizertatie.controller.ScanResponse;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.Scan;
import usv.dizertatie.repository.FindingRepository;
import usv.dizertatie.repository.ScanRepository;
import usv.dizertatie.scoring.SeverityScoringService;

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
    private final SeverityScoringService severityScoringService;

    /**
     * Returns a paginated list of all scans ordered by timestamp descending.
     * Each item includes summary metadata only (no finding details).
     *
     * @param page zero-based page index
     * @param size maximum number of items per page
     * @return paginated scan history
     */
    @Transactional(readOnly = true)
    public ScanHistoryResponse findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        var scanPage = scanRepository.findAllByOrderByTimestampDesc(pageable);

        List<ScanResponse> content = scanPage.getContent().stream()
                .map(scan -> {
                    List<Finding> findings = findingRepository.findByScanId(scan.getId());
                    double score = severityScoringService.score(findings);
                    return new ScanResponse(
                            scan.getId(),
                            scan.getUrl(),
                            scan.getMethod(),
                            scan.getModelId(),
                            scan.getTimestamp(),
                            score,
                            findings.size()
                    );
                })
                .toList();

        return new ScanHistoryResponse(content, page, size, scanPage.getTotalElements());
    }

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
}
