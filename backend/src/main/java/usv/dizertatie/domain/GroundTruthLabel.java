package usv.dizertatie.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ground_truth_labels")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroundTruthLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url;

    private String wcagCriterion;

    private String wcagLevel;

    private String severity;

    private String elementSelector;

    private String annotator;

    private String verifiedBy;

    private Double confidence;
}
