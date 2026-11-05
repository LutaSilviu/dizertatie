package usv.dizertatie.controller;

import com.microsoft.playwright.PlaywrightException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import usv.dizertatie.ai.AiAnalysisException;
import usv.dizertatie.ai.AiKeyMissingException;
import usv.dizertatie.axe.AxeParseException;
import usv.dizertatie.error.FailureMessages;
import usv.dizertatie.playwright.PageBlockedException;
import usv.dizertatie.playwright.PageCaptureException;

import java.util.Map;

/**
 * Turns every failure that escapes a controller into {@code {"error": "<human readable>"}} with a
 * meaningful status code, so the frontend can show the reason instead of an opaque 500.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        HttpStatusCode status = e.getStatusCode();
        String reason = e.getReason() != null ? e.getReason() : HttpStatus.valueOf(status.value()).getReasonPhrase();
        return error(status, reason);
    }

    /**
     * The target site refused to serve the page to the headless browser (bot challenge or
     * HTTP error), so there is nothing to audit. 502: the upstream site, not this API, failed.
     */
    @ExceptionHandler(PageBlockedException.class)
    public ResponseEntity<Map<String, String>> handlePageBlocked(PageBlockedException e) {
        return error(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    /** The page could not be loaded at all: DNS failure, connection refused, TLS error, ... */
    @ExceptionHandler(PageCaptureException.class)
    public ResponseEntity<Map<String, String>> handlePageCapture(PageCaptureException e) {
        log.warn("Page capture failed: {}", e.getMessage());
        boolean timeout = e.getCause() instanceof com.microsoft.playwright.TimeoutError;
        return error(timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    /** axe-core ran but returned something unparseable: the audit of the page failed. */
    @ExceptionHandler(AxeParseException.class)
    public ResponseEntity<Map<String, String>> handleAxeParse(AxeParseException e) {
        log.warn("axe-core result could not be parsed", e);
        return error(HttpStatus.BAD_GATEWAY, "The accessibility engine returned an unreadable result: " + e.getMessage());
    }

    @ExceptionHandler(PlaywrightException.class)
    public ResponseEntity<Map<String, String>> handlePlaywright(PlaywrightException e) {
        log.warn("Browser automation failed", e);
        boolean timeout = e instanceof com.microsoft.playwright.TimeoutError;
        return error(timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY, FailureMessages.describe(e));
    }

    /**
     * OpenAI call failed. A rejected/missing key is a server configuration problem (503);
     * anything else is an upstream failure (502). The message never carries OpenAI's raw text
     * for auth failures, which echoes a masked fragment of the key.
     */
    /** No usable key configured: refused before any browser or OpenAI work. */
    @ExceptionHandler(AiKeyMissingException.class)
    public ResponseEntity<Map<String, String>> handleAiKeyMissing(AiKeyMissingException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, FailureMessages.OPENAI_KEY_MISSING);
    }

    @ExceptionHandler(AiAnalysisException.class)
    public ResponseEntity<Map<String, String>> handleAi(AiAnalysisException e) {
        log.warn("AI analysis failed: {} ({})", e.getMessage(), FailureMessages.redact(rootMessage(e)));
        if (FailureMessages.isAiAuthFailure(e)) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, FailureMessages.OPENAI_KEY_REJECTED);
        }
        return error(HttpStatus.BAD_GATEWAY, FailureMessages.describe(e));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", rootMessage(e));
        return error(HttpStatus.CONFLICT, "The request conflicts with existing data (duplicate or missing required value)");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for '" + e.getName() + "': " + e.getValue());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParam(MissingServletRequestParameterException e) {
        return error(HttpStatus.BAD_REQUEST, "Missing request parameter '" + e.getParameterName() + "'");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Method " + e.getMethod() + " is not supported here");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type not supported; send application/json");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResource(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "Not found: /" + e.getResourcePath());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        // Spring MVC's own exceptions (406, missing path variable, ...) already know their status.
        if (e instanceof ErrorResponse errorResponse) {
            String detail = errorResponse.getBody().getDetail();
            return error(errorResponse.getStatusCode(), detail != null ? detail : e.getMessage());
        }
        log.error("Unhandled exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error: " + firstLine(rootMessage(e)));
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatusCode status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", message == null ? "Unknown error" : message));
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg != null ? msg : t.getClass().getSimpleName();
    }

    /** Playwright messages carry a multi-line call log; the first line is the useful part. */
    static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.strip();
        int nl = trimmed.indexOf('\n');
        return nl < 0 ? trimmed : trimmed.substring(0, nl).strip();
    }
}
