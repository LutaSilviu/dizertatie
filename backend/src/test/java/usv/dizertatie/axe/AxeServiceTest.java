package usv.dizertatie.axe;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AxeServiceTest {

    @Mock
    private Page page;

    private AxeService axeService;

    @BeforeEach
    void setUp() throws Exception {
        axeService = new AxeService();
        axeService.loadAxeScript();
    }

    @Test
    void run_injectsAxeIntoGlobalScopeThenReturnsViolationsJson() {
        when(page.evaluate(anyString())).thenReturn("[]");

        String result = axeService.run(page);

        assertThat(result).isEqualTo("[]");

        // axe-core must be added as a script tag (global scope), not evaluated inside
        // a function body, or `axe` is undefined when the audit call runs.
        ArgumentCaptor<Page.AddScriptTagOptions> captor =
                ArgumentCaptor.forClass(Page.AddScriptTagOptions.class);
        verify(page).addScriptTag(captor.capture());
        assertThat(captor.getValue().content).contains("axe");
    }
}
