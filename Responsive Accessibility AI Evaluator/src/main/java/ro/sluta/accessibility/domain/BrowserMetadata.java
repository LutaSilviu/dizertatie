package ro.sluta.accessibility.domain;

public record BrowserMetadata(
        String playwrightVersion,
        String browserName,
        String browserVersion,
        boolean headless,
        String locale,
        String timezoneId,
        double deviceScaleFactor,
        String colorScheme,
        String reducedMotion,
        boolean isolatedContext,
        boolean serviceWorkersBlocked,
        boolean downloadsBlocked,
        boolean popupsBlocked) {
}
