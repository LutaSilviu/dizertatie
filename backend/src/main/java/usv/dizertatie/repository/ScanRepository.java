package usv.dizertatie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import usv.dizertatie.domain.Scan;

public interface ScanRepository extends JpaRepository<Scan, Long> {
}
