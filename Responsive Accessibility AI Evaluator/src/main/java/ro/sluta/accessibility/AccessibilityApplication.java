package ro.sluta.accessibility;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AccessibilityApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccessibilityApplication.class, args);
    }
}
