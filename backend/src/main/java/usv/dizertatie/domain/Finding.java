package usv.dizertatie.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "findings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private Scan scan;

    private String sourceMethod;

    private String wcagCriterion;

    private String wcagLevel;

    private String severity;

    private String elementSelector;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String suggestion;

    private Double confidence;
}
