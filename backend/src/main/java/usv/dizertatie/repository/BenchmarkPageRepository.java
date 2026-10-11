package usv.dizertatie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import usv.dizertatie.domain.BenchmarkPage;

public interface BenchmarkPageRepository extends JpaRepository<BenchmarkPage, Long> {
}
