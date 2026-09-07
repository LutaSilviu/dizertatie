package ro.sluta.accessibility.persistence;

import java.time.Instant;
import java.util.UUID;

public record ExperimentConfigurationRecord(UUID configurationId, String name, String configurationVersion,
        String configurationJson, String configurationHash, Instant frozenAt, long version) { }
