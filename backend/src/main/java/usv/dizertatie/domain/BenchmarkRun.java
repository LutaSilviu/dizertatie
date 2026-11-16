package usv.dizertatie.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "benchmark_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private int totalPages;

    private int pagesDone;

    /** Comma-separated scan methods requested (subset of AXE,AI,COMBINED); null = all three (pre-V7 runs). */
    @Column(length = 100)
    private String methods;

    /** Human-readable summary of failed scans; null when nothing failed. */
    @Column(columnDefinition = "TEXT")
    private String errorSummary;
}
