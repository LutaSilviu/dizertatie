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
        "OPENAI_API_KEY=test-key",
        "skipPlaywrightInstall=true"
})
class OpenAiModelSelectorTest {

    @Autowired
    private OpenAiModelSelector selector;

    @Test
    void getModelId_gpt4o_returnsCorrectId() {
        assertThat(selector.getModelId(AiModel.GPT4O)).isEqualTo("gpt-4o");
    }

    @Test
    void getModelId_gpt4oMini_returnsCorrectId() {
        assertThat(selector.getModelId(AiModel.GPT4O_MINI)).isEqualTo("gpt-4o-mini");
    }
}
