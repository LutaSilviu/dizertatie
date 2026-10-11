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
}
