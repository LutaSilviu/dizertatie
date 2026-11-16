package usv.dizertatie.ai;

import usv.dizertatie.error.FailureMessages;

/**
 * No usable OpenAI key is configured (blank or an obvious placeholder), so an AI scan is refused
 * up front instead of launching a browser and sending the placeholder to OpenAI.
 */
public class AiKeyMissingException extends AiAnalysisException {

    public AiKeyMissingException() {
        super(FailureMessages.OPENAI_KEY_MISSING);
    }
}
