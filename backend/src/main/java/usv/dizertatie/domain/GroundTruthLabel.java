package usv.dizertatie.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    // axe-style selectors exceed the 255-character default; the Postgres column is TEXT (V1).
    @Column(length = 1024)
    private String elementSelector;

    private String annotator;

    private String verifiedBy;

    private Double confidence;

    /** Why the element fails the criterion (from the dataset); shown with false-negative examples. */
    @Column(columnDefinition = "TEXT")
    private String rationale;

    private LocalDateTime createdAt;
}
