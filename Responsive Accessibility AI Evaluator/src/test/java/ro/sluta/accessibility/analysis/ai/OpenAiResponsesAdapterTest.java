package ro.sluta.accessibility.analysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiResponsesAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void usesOfficialResponsesApiWithStrictSchemaAndCapturesUsageAndRequestId() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        String responseBody = providerResponse();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("x-request-id", "req_local_fake");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var properties = AiTestFixtures.properties("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-server-key");
            var adapter = new OpenAiResponsesAdapter(properties, mapper);
            AiProviderResponse response = adapter.analyze(new AiProviderRequest("gpt-5-nano", AiCondition.AI_MULTIMODAL,
                    "instructions", new PageContext("PAGE_CONTEXT_V1", "context", 7, 7, false, "a".repeat(64)),
                    "data:image/png;base64,AQID", schema(), 8000));

            assertThat(response.outputText()).isEqualTo(AiTestFixtures.VALID.strip());
            assertThat(response.usage()).isEqualTo(new AiUsage(100, 20, 30, 130));
            assertThat(response.requestId()).isEqualTo("req_local_fake");
            assertThat(mapper.readTree(response.rawResponse()).get("outputText").asText()).isEqualTo(AiTestFixtures.VALID.strip());
            assertThat(authorization.get()).isEqualTo("Bearer test-server-key");
            assertThat(body.get()).contains("\"model\":\"gpt-5-nano\"", "\"type\":\"json_schema\"",
                    "\"strict\":true", "\"detail\":\"high\"", "data:image/png;base64,AQID");
        } finally { server.stop(0); }
    }

    @Test void neverAttemptsNetworkWithoutServerSideKey() {
        var adapter = new OpenAiResponsesAdapter(AiTestFixtures.properties("http://127.0.0.1:1/v1", ""), mapper);
        assertThatThrownBy(() -> adapter.analyze(new AiProviderRequest("gpt-5-nano", AiCondition.AI_TEXT,
                "prompt", new PageContext("v", "context", 7, 7, false, "a".repeat(64)), null, schema(), 8000)))
                .isInstanceOf(AiProviderException.class)
                .extracting(error -> ((AiProviderException) error).code()).isEqualTo(AiErrorCode.AI_NOT_CONFIGURED);
    }

    private String providerResponse() throws Exception {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", "resp_local_fake"); response.put("object", "response"); response.put("created_at", 1788192000);
        response.put("status", "completed"); response.put("background", false); response.put("error", null);
        response.put("incomplete_details", null); response.put("instructions", null); response.put("max_output_tokens", 8000);
        response.put("model", "gpt-5-nano-2026-08-07");
        response.put("output", List.of(Map.of("type", "message", "id", "msg_1", "status", "completed", "role", "assistant",
                "content", List.of(Map.of("type", "output_text", "annotations", List.of(), "text", AiTestFixtures.VALID.strip())))));
        response.put("parallel_tool_calls", true); response.put("previous_response_id", null);
        response.put("reasoning", Map.of("effort", "minimal")); response.put("store", false);
        response.put("temperature", 1); response.put("text", Map.of("format", Map.of("type", "json_schema")));
        response.put("tool_choice", "auto"); response.put("tools", List.of()); response.put("top_p", 1);
        response.put("truncation", "disabled");
        response.put("usage", Map.of("input_tokens", 100, "input_tokens_details", Map.of("cached_tokens", 20),
                "output_tokens", 30, "output_tokens_details", Map.of("reasoning_tokens", 0), "total_tokens", 130));
        response.put("user", null); response.put("metadata", Map.of());
        return mapper.writeValueAsString(response);
    }

    private String schema() { return new AiContractResources(AiTestFixtures.properties("x", "x")).schema(); }
}
