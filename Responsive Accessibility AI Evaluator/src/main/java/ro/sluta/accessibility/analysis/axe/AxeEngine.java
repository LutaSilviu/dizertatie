package ro.sluta.accessibility.analysis.axe;

import com.deque.html.axecore.results.AxeResults;
import com.microsoft.playwright.Page;
import java.util.List;

public interface AxeEngine {
    AxeResults analyze(Page page, List<String> tags);
    String adapterVersion();
}
