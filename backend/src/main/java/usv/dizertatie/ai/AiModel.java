package usv.dizertatie.ai;

public enum AiModel {

    GPT4O("gpt-4o"),
    GPT4O_MINI("gpt-4o-mini");

    private final String modelId;

    AiModel(String modelId) {
        this.modelId = modelId;
    }

    public String getModelId() {
        return modelId;
    }

    public static AiModel fromId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return GPT4O_MINI;
        }
        for (AiModel m : values()) {
            if (m.modelId.equalsIgnoreCase(modelId) || m.name().equalsIgnoreCase(modelId)) {
                return m;
            }
        }
        return GPT4O_MINI;
    }
}
