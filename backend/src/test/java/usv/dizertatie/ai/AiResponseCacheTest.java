package usv.dizertatie.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiResponseCacheTest {

    private AiResponseCache cache;

    @BeforeEach
    void setUp() {
        cache = new AiResponseCache();
    }

    @Test
    void sameHtmlAndModel_apiCallInvokedOnlyOnce() {
        // Arrange
        String html = "<html><body><img src='test.png'/></body></html>";
        String promptVersion = "v1";
        String expectedResponse = "{\"findings\":[]}";

        @SuppressWarnings("unchecked")
        Supplier<String> apiCall = mock(Supplier.class);
        when(apiCall.get()).thenReturn(expectedResponse);

        // Act
        String first = cache.getOrCompute(html, AiModel.GPT4O, promptVersion, apiCall);
        String second = cache.getOrCompute(html, AiModel.GPT4O, promptVersion, apiCall);

        // Assert
        assertThat(first).isEqualTo(expectedResponse);
        assertThat(second).isEqualTo(expectedResponse);
        verify(apiCall, times(1)).get();
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void differentHtml_twoSeparateApiCalls() {
        // Arrange
        String html1 = "<html><body><img src='a.png'/></body></html>";
        String html2 = "<html><body><img src='b.png'/></body></html>";
        String promptVersion = "v1";
        String response1 = "{\"findings\":[{\"id\":\"1\"}]}";
        String response2 = "{\"findings\":[{\"id\":\"2\"}]}";

        @SuppressWarnings("unchecked")
        Supplier<String> apiCall1 = mock(Supplier.class);
        @SuppressWarnings("unchecked")
        Supplier<String> apiCall2 = mock(Supplier.class);
        when(apiCall1.get()).thenReturn(response1);
        when(apiCall2.get()).thenReturn(response2);

        // Act
        String result1 = cache.getOrCompute(html1, AiModel.GPT4O, promptVersion, apiCall1);
        String result2 = cache.getOrCompute(html2, AiModel.GPT4O, promptVersion, apiCall2);

        // Assert
        assertThat(result1).isEqualTo(response1);
        assertThat(result2).isEqualTo(response2);
        verify(apiCall1, times(1)).get();
        verify(apiCall2, times(1)).get();
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void differentModel_twoSeparateApiCalls() {
        // Arrange
        String html = "<html><body><img src='test.png'/></body></html>";
        String promptVersion = "v1";
        String responseGpt4o = "{\"findings\":[{\"model\":\"gpt-4o\"}]}";
        String responseGpt4oMini = "{\"findings\":[{\"model\":\"gpt-4o-mini\"}]}";

        @SuppressWarnings("unchecked")
        Supplier<String> apiCallGpt4o = mock(Supplier.class);
        @SuppressWarnings("unchecked")
        Supplier<String> apiCallGpt4oMini = mock(Supplier.class);
        when(apiCallGpt4o.get()).thenReturn(responseGpt4o);
        when(apiCallGpt4oMini.get()).thenReturn(responseGpt4oMini);

        // Act
        String result1 = cache.getOrCompute(html, AiModel.GPT4O, promptVersion, apiCallGpt4o);
        String result2 = cache.getOrCompute(html, AiModel.GPT4O_MINI, promptVersion, apiCallGpt4oMini);

        // Assert
        assertThat(result1).isEqualTo(responseGpt4o);
        assertThat(result2).isEqualTo(responseGpt4oMini);
        verify(apiCallGpt4o, times(1)).get();
        verify(apiCallGpt4oMini, times(1)).get();
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void clear_emptiesCache() {
        // Arrange
        String html = "<html><body/></html>";
        @SuppressWarnings("unchecked")
        Supplier<String> apiCall = mock(Supplier.class);
        when(apiCall.get()).thenReturn("{\"findings\":[]}");
        cache.getOrCompute(html, AiModel.GPT4O, "v1", apiCall);
        assertThat(cache.size()).isEqualTo(1);

        // Act
        cache.clear();

        // Assert
        assertThat(cache.size()).isEqualTo(0);
    }
}
