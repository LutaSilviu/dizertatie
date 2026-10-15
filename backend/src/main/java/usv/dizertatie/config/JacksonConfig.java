package usv.dizertatie.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a Jackson 2.x {@link ObjectMapper} bean for components that require it.
 * Spring Boot 4.x auto-configures Jackson 3.x; this bean bridges the gap for any
 * component that still depends on the 2.x API.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
