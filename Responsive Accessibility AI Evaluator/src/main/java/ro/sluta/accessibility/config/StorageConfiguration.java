package ro.sluta.accessibility.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class StorageConfiguration {
    @Bean
    Path snapshotStorageRoot(@Value("${app.storage.directory:./var/data}") String directory) {
        return Path.of(directory).toAbsolutePath().normalize();
    }
}
