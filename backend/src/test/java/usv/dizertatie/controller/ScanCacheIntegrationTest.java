package usv.dizertatie.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import usv.dizertatie.ai.OpenAiModelSelector;
import usv.dizertatie.playwright.PageCapture;
import usv.dizertatie.playwright.PageCaptureService;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cachetest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "APP_API_KEY=test-key", "spring.ai.openai.api-key=test-key"
})
@AutoConfigureMockMvc
class ScanCacheIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean PageCaptureService captureService;
    @MockitoBean OpenAiModelSelector modelSelector;

    @Test
    void repeatedScanReportsCacheHitAndAvoidsAnotherModelCall() throws Exception {
        when(captureService.capture(anyString())).thenReturn(new PageCapture("<html></html>", new byte[]{1}));
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        when(modelSelector.getClient(any())).thenReturn(client);
        when(client.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(any(Consumer.class))).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.content()).thenReturn("{\"findings\":[]}");

        String scan = "{\"url\":\"https://example.com\",\"method\":\"AI\",\"model\":\"gpt-4o\"}";
        mockMvc.perform(post("/scans").header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(scan))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cacheHit").value(false));
        mockMvc.perform(post("/scans").header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(scan))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cacheHit").value(true));
        verify(request, times(1)).call();
    }
}
