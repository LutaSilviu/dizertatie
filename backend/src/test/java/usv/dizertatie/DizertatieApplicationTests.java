package usv.dizertatie;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import usv.dizertatie.service.BenchmarkRunExecutor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
		"APP_API_KEY=test-secret-key",
		"spring.ai.openai.api-key=test-openai-key",
		"spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false"
})
class DizertatieApplicationTests {

	@Autowired
	private BenchmarkRunExecutor benchmarkRunExecutor;

	@Test
	void contextLoads() {
	}

	/** Benchmark runs take minutes; they must not execute on the HTTP request thread. */
	@Test
	void benchmarkRunExecutor_isAsyncProxy() {
		assertThat(AopUtils.isAopProxy(benchmarkRunExecutor)).isTrue();
	}

}
