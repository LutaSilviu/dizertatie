package usv.dizertatie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import usv.dizertatie.domain.BenchmarkPage;

import java.util.List;

public interface BenchmarkPageRepository extends JpaRepository<BenchmarkPage, Long> {

    List<BenchmarkPage> findByRunId(Long runId);

    long countByRunIdAndStatus(Long runId, String status);
}
