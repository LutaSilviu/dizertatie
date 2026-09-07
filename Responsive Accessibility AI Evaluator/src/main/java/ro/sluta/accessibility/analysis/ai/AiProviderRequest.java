package ro.sluta.accessibility.analysis.ai;

public record AiProviderRequest(
        String providerModel,
        AiCondition condition,
        String instructions,
        PageContext context,
        String screenshotDataUrl,
        String schemaJson,
        int maxOutputTokens) {
}
