package usv.dizertatie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.Scan;

import java.util.List;

public interface FindingRepository extends JpaRepository<Finding, Long> {

    List<Finding> findByScan(Scan scan);

    List<Finding> findByScanId(Long scanId);
}
