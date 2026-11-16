package usv.dizertatie.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import usv.dizertatie.ai.AiStatusService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "OpenAI availability")
public class AiStatusController {

    private final AiStatusService aiStatusService;

    /**
     * {@code {"status": "OK"|"MISSING_KEY"|"INVALID_KEY"|"UNREACHABLE", "message": "..."}}; always 200.
     * Cached for 60 seconds.
     */
    @Operation(summary = "Whether AI scans can run (OpenAI key configured and accepted)")
    @GetMapping("/status")
    public Map<String, String> status() {
        AiStatusService.AiStatus s = aiStatusService.status();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", s.status().name());
        body.put("message", s.message());
        return body;
    }
}
