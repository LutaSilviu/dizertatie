package usv.dizertatie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import usv.dizertatie.domain.BenchmarkPage;

import java.util.List;

public interface BenchmarkPageRepository extends JpaRepository<BenchmarkPage, Long> {

    List<BenchmarkPage> findByRunId(Long runId);

    long countByRunIdAndStatus(Long runId, String status);

    @Query("SELECT AVG(p.latencyAxeMs) FROM BenchmarkPage p WHERE p.run.id = :runId AND p.latencyAxeMs IS NOT NULL")
    Double avgLatencyAxeMsByRunId(@Param("runId") Long runId);

    @Query("SELECT AVG(p.latencyAiMs) FROM BenchmarkPage p WHERE p.run.id = :runId AND p.latencyAiMs IS NOT NULL")
    Double avgLatencyAiMsByRunId(@Param("runId") Long runId);

    @Query("SELECT AVG(p.latencyCombinedMs) FROM BenchmarkPage p WHERE p.run.id = :runId AND p.latencyCombinedMs IS NOT NULL")
    Double avgLatencyCombinedMsByRunId(@Param("runId") Long runId);

    @Query("SELECT AVG(p.estimatedCostUsd) FROM BenchmarkPage p WHERE p.run.id = :runId AND p.estimatedCostUsd IS NOT NULL")
    Double avgEstimatedCostUsdByRunId(@Param("runId") Long runId);
}
