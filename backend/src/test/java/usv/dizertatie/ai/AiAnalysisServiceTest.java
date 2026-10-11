package usv.dizertatie.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    @Mock
    private OpenAiModelSelector openAiModelSelector;

    @Mock
    private PromptLoader promptLoader;

    @InjectMocks
    private AiAnalysisService aiAnalysisService;

    @Test
    void analyze_gpt4o_returnsRawJsonString() {
        // Arrange
        String html = "<html><body><img src='test.png'/></body></html>";
        String systemPrompt = "You are an accessibility auditor.";
        String expectedResponse = "{\"findings\":[]}";

        ChatClient mockChatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec mockSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec mockCallSpec = mock(ChatClient.CallResponseSpec.class);

        when(promptLoader.getPrompt()).thenReturn(systemPrompt);
        when(openAiModelSelector.getClient(AiModel.GPT4O)).thenReturn(mockChatClient);
        when(mockChatClient.prompt()).thenReturn(mockSpec);
        when(mockSpec.system(anyString())).thenReturn(mockSpec);
        when(mockSpec.user(anyString())).thenReturn(mockSpec);
        when(mockSpec.call()).thenReturn(mockCallSpec);
        when(mockCallSpec.content()).thenReturn(expectedResponse);

        // Act
        String result = aiAnalysisService.analyze(html, AiModel.GPT4O);

        // Assert
        assertThat(result).isEqualTo(expectedResponse);
    }
}
