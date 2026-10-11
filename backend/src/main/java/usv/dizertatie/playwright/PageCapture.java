package usv.dizertatie.playwright;

/**
 * Holds the results of a single Playwright page capture session:
 * the rendered HTML and a full-page PNG screenshot.
 */
public record PageCapture(String html, byte[] screenshotPng) {
}
