package usv.dizertatie;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"APP_API_KEY=test-secret-key",
		"spring.ai.openai.api-key=test-openai-key"
})
class DizertatieApplicationTests {

	@Test
	void contextLoads() {
	}

}
