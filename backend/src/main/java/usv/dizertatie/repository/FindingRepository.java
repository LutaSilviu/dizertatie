package usv.dizertatie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import usv.dizertatie.domain.Finding;

import java.util.List;

public interface FindingRepository extends JpaRepository<Finding, Long> {

    long countByScanId(Long scanId);

    List<Finding> findByScanId(Long scanId);
}
