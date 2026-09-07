package ro.sluta.accessibility.analysis.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.ResponseTextConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.config.AiAnalysisProperties;

@Component
public class OpenAiResponsesAdapter implements AiAnalysisPort {
    private final AiAnalysisProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiResponsesAdapter(AiAnalysisProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProviderResponse analyze(AiProviderRequest request) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new AiProviderException(AiErrorCode.AI_NOT_CONFIGURED, false,
                    "OPENAI_API_KEY nu este configurată pe server.", null);
        }
        long started = System.nanoTime();
        OpenAIClient client = client(properties.timeout());
        try {
            var raw = client.responses().withRawResponse().create(params(request));
            String requestId = raw.requestId().orElse(null);
            Response response = raw.parse();
            StringBuilder text = new StringBuilder();
            boolean refusal = false;
            for (var item : response.output()) {
                if (!item.isMessage()) continue;
                for (var content : item.asMessage().content()) {
                    if (content.isOutputText()) text.append(content.asOutputText().text());
                    if (content.isRefusal()) refusal = true;
                }
            }
            AiUsage usage = response.usage().map(value -> new AiUsage(
                    value.inputTokens(), value.inputTokensDetails().cachedTokens(),
                    value.outputTokens(), value.totalTokens())).orElse(AiUsage.empty());
            boolean truncated = response.status().map(Object::toString).orElse("").contains("INCOMPLETE")
                    || response.incompleteDetails().isPresent();
            String reportedModel = response.model().isString() ? response.model().asString() : response.model().toString();
            return new AiProviderResponse(rawEnvelope(response, text.toString(), usage, requestId), text.toString(),
                    usage, response.id(), requestId, reportedModel, elapsed(started), refusal, truncated);
        } catch (RateLimitException exception) {
            throw new AiProviderException(AiErrorCode.AI_RATE_LIMITED, true, "Furnizorul a limitat rata apelurilor.", exception);
        } catch (OpenAIIoException exception) {
            AiErrorCode code = causedByTimeout(exception) ? AiErrorCode.AI_TIMEOUT : AiErrorCode.AI_PROVIDER_ERROR;
            throw new AiProviderException(code, true, "Apelul AI nu a putut fi finalizat.", exception);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiProviderException(AiErrorCode.AI_PROVIDER_ERROR, false, "Furnizorul AI a returnat o eroare.", exception);
        } finally {
            client.close();
        }
    }

    private OpenAIClient client(Duration timeout) {
        return OpenAIOkHttpClient.builder().apiKey(properties.apiKey()).baseUrl(properties.baseUrl())
                .timeout(timeout).maxRetries(0).build();
    }

    private ResponseCreateParams params(AiProviderRequest request) {
        var schema = ResponseFormatTextJsonSchemaConfig.Schema.builder()
                .additionalProperties(schemaProperties(request.schemaJson())).build();
        var format = ResponseFormatTextJsonSchemaConfig.builder().name("accessibility_findings")
                .description("Structured accessibility findings").schema(schema).strict(true).build();
        var builder = ResponseCreateParams.builder().model(request.providerModel())
                .instructions(request.instructions()).maxOutputTokens(request.maxOutputTokens()).store(false)
                .text(ResponseTextConfig.builder().format(format).build());
        if (request.condition() == AiCondition.AI_MULTIMODAL) {
            List<ResponseInputContent> content = new ArrayList<>();
            content.add(ResponseInputContent.ofInputText(ResponseInputText.builder().text(request.context().text()).build()));
            content.add(ResponseInputContent.ofInputImage(ResponseInputImage.builder()
                    .detail(ResponseInputImage.Detail.HIGH).imageUrl(request.screenshotDataUrl()).build()));
            var message = EasyInputMessage.builder().role(EasyInputMessage.Role.USER)
                    .contentOfResponseInputMessageContentList(content).build();
            builder.input(ResponseCreateParams.Input.ofResponse(List.of(ResponseInputItem.ofEasyInputMessage(message))));
        } else {
            builder.input(request.context().text());
        }
        return builder.build();
    }

    private Map<String, JsonValue> schemaProperties(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() { });
            return map.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                    entry -> JsonValue.from(entry.getValue())));
        } catch (Exception exception) {
            throw new IllegalStateException("Schema AI nu poate fi încărcată.", exception);
        }
    }

    private String rawEnvelope(Response response, String outputText, AiUsage usage, String requestId) {
        try {
            Map<String, Object> value = new java.util.LinkedHashMap<>();
            value.put("sdkRepresentation", response.toString());
            value.put("id", response.id());
            value.put("model", response.model().isString() ? response.model().asString() : response.model().toString());
            value.put("status", response.status().map(Object::toString).orElse(null));
            value.put("requestId", requestId);
            value.put("outputText", outputText);
            value.put("usage", usage);
            value.put("error", response.error().map(Object::toString).orElse(null));
            value.put("incompleteDetails", response.incompleteDetails().map(Object::toString).orElse(null));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Răspunsul SDK nu poate fi serializat pentru audit.", exception);
        }
    }

    private static long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
    private static boolean causedByTimeout(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause())
            if (current instanceof java.net.SocketTimeoutException || current instanceof java.net.http.HttpTimeoutException) return true;
        return false;
    }
}
