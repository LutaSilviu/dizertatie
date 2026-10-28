package usv.dizertatie.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "scans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Scan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String method;

    private String modelId;

    private String promptVersion;

    private String axeVersion;

    private String browserVersion;

    @Column(columnDefinition = "TEXT")
    private String rawAxeIncomplete;

    private Boolean cacheHit;

    @Column(columnDefinition = "TEXT")
    private String rawAiResponse;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScanStatus status = ScanStatus.COMPLETED;

    @Column(columnDefinition = "TEXT")
    private String failureReason;
}
