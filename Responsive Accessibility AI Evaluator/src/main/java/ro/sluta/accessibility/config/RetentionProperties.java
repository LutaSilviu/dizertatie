package ro.sluta.accessibility.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.retention")
public record RetentionProperties(Policy policy) {
    public RetentionProperties { policy = policy == null ? Policy.KEEP_ALL : policy; }
    public enum Policy { KEEP_ALL, MANUAL_AFTER_BACKUP }
}
