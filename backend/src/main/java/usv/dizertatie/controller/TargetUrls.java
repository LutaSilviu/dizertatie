package usv.dizertatie.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Validates the URL a user asks us to scan before a browser is launched for it, so a typo
 * yields a 400 with a readable message instead of a Playwright protocol error.
 */
final class TargetUrls {

    private TargetUrls() {
    }

    /**
     * Trims the URL, adds {@code https://} when no scheme was typed ("example.com"), and
     * requires an absolute http(s) URL with a host.
     *
     * @throws ResponseStatusException 400 when the URL is missing or not a valid http(s) URL
     */
    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw badRequest("A URL to scan is required");
        }
        String url = raw.strip();
        if (!url.contains("://")) {
            url = "https://" + url;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw badRequest("Only http:// and https:// URLs can be scanned: " + raw);
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw badRequest("URL has no host name: " + raw);
            }
            return url;
        } catch (URISyntaxException e) {
            throw badRequest("Not a valid URL: " + raw);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
