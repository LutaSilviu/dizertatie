package usv.dizertatie.playwright;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "APP_API_KEY=test-key",
        "skipPlaywrightInstall=true"
})
class PlaywrightConfigTest {

    @Test
    void contextLoads() {
        // Verifies that the Spring context starts successfully with PlaywrightConfig present.
        // The BrowserType bean is @Lazy so no actual browser is launched during context startup.
    }
}
