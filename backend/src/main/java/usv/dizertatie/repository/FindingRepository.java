package usv.dizertatie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import usv.dizertatie.domain.Finding;

public interface FindingRepository extends JpaRepository<Finding, Long> {
}
