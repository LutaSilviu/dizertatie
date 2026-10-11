package usv.dizertatie.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "APP_API_KEY=test-key",
        "spring.ai.openai.api-key=test-key",
        "skipPlaywrightInstall=true"
})
class PromptLoaderTest {

    @Autowired
    private PromptLoader promptLoader;

    @Test
    void getPromptReturnsNonEmptyString() {
        assertThat(promptLoader.getPrompt())
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    void getVersionReturnsV1() {
        assertThat(promptLoader.getVersion()).isEqualTo("v1");
    }

    @Test
    void promptTextContainsJson() {
        assertThat(promptLoader.getPrompt())
                .containsIgnoringCase("json");
    }
}
