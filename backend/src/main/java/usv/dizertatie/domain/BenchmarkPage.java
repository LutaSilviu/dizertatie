package usv.dizertatie.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "benchmark_pages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private BenchmarkRun run;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String status;

    private Long axeScanId;

    private Long aiScanId;

    private Long combinedScanId;

    private Long latencyAxeMs;

    private Long latencyAiMs;

    private Long latencyCombinedMs;

    private Double estimatedCostUsd;
}
