package usv.dizertatie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import usv.dizertatie.domain.BenchmarkRun;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, Long> {
}
