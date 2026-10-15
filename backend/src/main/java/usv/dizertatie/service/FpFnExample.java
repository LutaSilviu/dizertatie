package usv.dizertatie.service;

/**
 * A single false-positive or false-negative example surfaced in the metrics response.
 *
 * @param method      detection method that produced the wrong result (AXE / AI / COMBINED)
 * @param url         page URL where the error was found
 * @param criterion   WCAG criterion identifier (e.g. "1.1.1")
 * @param element     CSS selector of the offending element
 * @param description human-readable description of the finding
 */
public record FpFnExample(String method, String url, String criterion,
                          String element, String description) {
}
