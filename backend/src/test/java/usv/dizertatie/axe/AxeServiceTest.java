package usv.dizertatie.axe;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
    void run_returnsViolationsJson() {
        // First call (inject script) returns null, second call (axe.run) returns "[]"
        when(page.evaluate(anyString()))
                .thenReturn(null)
                .thenReturn("[]");

        String result = axeService.run(page);

        assertThat(result).isEqualTo("[]");
    }
}
