package ro.sluta.accessibility.browser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.domain.SnapshotErrorCode;
import ro.sluta.accessibility.security.NetworkAccessPolicy;

@Component
public class SafeRedirectResolver {
    private final NetworkAccessPolicy networkPolicy;

    public SafeRedirectResolver(NetworkAccessPolicy networkPolicy) {
        this.networkPolicy = networkPolicy;
    }

    public ResolvedDestination resolve(URI requested, boolean mainDocument, int maxRedirects,
                                       Duration timeout, long maxDeclaredBytes) {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(timeout).build();
        URI current = requested;
        int redirects = 0;
        long deadline = System.nanoTime() + timeout.toNanos();

        while (true) {
            networkPolicy.verify(current, mainDocument);
            HttpResponse<Void> response = requestHeaders(client, current, remaining(deadline, mainDocument),
                    mainDocument);
            long declaredLength = response.headers().firstValueAsLong("content-length").orElse(-1);
            if (mainDocument && declaredLength > maxDeclaredBytes) {
                throw new RedirectResolutionException(SnapshotErrorCode.MAIN_DOCUMENT_TOO_LARGE,
                        "Documentul principal declarat depășește limita de 10 MB.");
            }
            if (!isRedirect(response.statusCode())) {
                return new ResolvedDestination(current, redirects, response.statusCode(), declaredLength);
            }
            String location = response.headers().firstValue("location").orElseThrow(() ->
                    new RedirectResolutionException(code(mainDocument),
                            "Răspunsul de redirecționare nu conține antetul Location."));
            redirects++;
            if (redirects > maxRedirects) {
                throw new RedirectResolutionException(SnapshotErrorCode.REDIRECT_LIMIT_EXCEEDED,
                        "Pagina a depășit limita de redirecționări.");
            }
            current = current.resolve(location);
            networkPolicy.verify(current, mainDocument);
        }
    }

    private HttpResponse<Void> requestHeaders(HttpClient client, URI uri, Duration timeout,
                                              boolean mainDocument) {
        HttpRequest head = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("User-Agent", "Responsive-Accessibility-AI-F2/1")
                .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        try {
            HttpResponse<Void> response = client.send(head, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 405 && response.statusCode() != 501) return response;
            HttpRequest get = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("User-Agent", "Responsive-Accessibility-AI-F2/1").GET().build();
            return client.send(get, HttpResponse.BodyHandlers.discarding());
        } catch (HttpTimeoutException exception) {
            throw new RedirectResolutionException(mainDocument ? SnapshotErrorCode.PAGE_LOAD_TIMEOUT
                    : SnapshotErrorCode.BLOCKED_REQUEST, "Prevalidarea HTTP a depășit limita de timp.", exception);
        } catch (IOException exception) {
            throw new RedirectResolutionException(mainDocument ? SnapshotErrorCode.PAGE_UNAVAILABLE
                    : SnapshotErrorCode.BLOCKED_REQUEST, "Destinația HTTP nu este disponibilă.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RedirectResolutionException(mainDocument ? SnapshotErrorCode.PAGE_UNAVAILABLE
                    : SnapshotErrorCode.BLOCKED_REQUEST, "Prevalidarea HTTP a fost întreruptă.", exception);
        }
    }

    private Duration remaining(long deadline, boolean mainDocument) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new RedirectResolutionException(mainDocument
                ? SnapshotErrorCode.PAGE_LOAD_TIMEOUT : SnapshotErrorCode.BLOCKED_REQUEST,
                "Prevalidarea redirecționărilor a depășit limita de timp.");
        return Duration.ofNanos(nanos);
    }

    private boolean isRedirect(int status) { return status >= 300 && status < 400; }
    private SnapshotErrorCode code(boolean mainDocument) {
        return mainDocument ? SnapshotErrorCode.PAGE_UNAVAILABLE : SnapshotErrorCode.BLOCKED_REQUEST;
    }

    public record ResolvedDestination(URI uri, int redirectCount, int responseStatus,
                                      long declaredContentLength) { }
}
