package ro.sluta.accessibility.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AbandonedRunRecovery implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbandonedRunRecovery.class);
    private final ResearchPersistenceService persistence;

    public AbandonedRunRecovery(ResearchPersistenceService persistence) {
        this.persistence = persistence;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        int abandoned = persistence.markAbandonedRuns();
        if (abandoned > 0) {
            LOGGER.warn("Au fost marcate explicit ABANDONED {} rulări rămase RUNNING la restart.", abandoned);
        }
    }
}
