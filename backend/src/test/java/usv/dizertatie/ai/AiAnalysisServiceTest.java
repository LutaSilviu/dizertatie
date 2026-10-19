package usv.dizertatie.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import usv.dizertatie.playwright.PageCapture;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    private static final byte[] SCREENSHOT = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);

    @Mock
    private OpenAiModelSelector openAiModelSelector;

    @Mock
    private PromptLoader promptLoader;

    @Mock
    private AiResponseCache aiResponseCache;

    @InjectMocks
    private AiAnalysisService aiAnalysisService;

    /** Captures what the service puts into the user message. */
    private static final class CapturedUserMessage {
        private String text;
        private final List<Media> media = new ArrayList<>();
    }

    private CapturedUserMessage stubChatClient(String html, String response) {
        CapturedUserMessage captured = new CapturedUserMessage();

        ChatClient mockChatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec mockSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec mockCallSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.PromptUserSpec mockUserSpec = mock(ChatClient.PromptUserSpec.class);

        when(promptLoader.getPrompt()).thenReturn("You are an accessibility auditor.");
        when(promptLoader.getVersion()).thenReturn("v1");
        when(openAiModelSelector.getClient(AiModel.GPT4O)).thenReturn(mockChatClient);
        when(mockChatClient.prompt()).thenReturn(mockSpec);
        when(mockSpec.system(anyString())).thenReturn(mockSpec);
        when(mockSpec.call()).thenReturn(mockCallSpec);
        when(mockCallSpec.content()).thenReturn(response);

        when(mockUserSpec.text(anyString())).thenAnswer(inv -> {
            captured.text = inv.getArgument(0);
            return mockUserSpec;
        });
        when(mockUserSpec.media(any(Media.class))).thenAnswer(inv -> {
            for (Object arg : inv.getArguments()) {
                captured.media.add((Media) arg);
            }
            return mockUserSpec;
        });

        when(mockSpec.user(any(Consumer.class))).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<ChatClient.PromptUserSpec> consumer = inv.getArgument(0);
            consumer.accept(mockUserSpec);
            return mockSpec;
        });

        when(aiResponseCache.getOrCompute(anyString(), any(AiModel.class), anyString(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<String> supplier = invocation.getArgument(3);
                    return supplier.get();
                });

        return captured;
    }

    @Test
    void analyze_sendsDomTextAndScreenshotToOpenAi() {
        String html = "<html><body><img src='test.png'/></body></html>";
        String expectedResponse = "{\"findings\":[]}";
        CapturedUserMessage captured = stubChatClient(html, expectedResponse);

        String result = aiAnalysisService.analyze(new PageCapture(html, SCREENSHOT), AiModel.GPT4O);

        assertThat(result).isEqualTo(expectedResponse);
        assertThat(captured.text).isEqualTo(html);
        assertThat(captured.media)
                .as("the page screenshot must be attached to the OpenAI request")
                .hasSize(1);
        assertThat(captured.media.get(0).getDataAsByteArray()).isEqualTo(SCREENSHOT);
        assertThat(captured.media.get(0).getMimeType().toString()).isEqualTo("image/png");
    }
}
