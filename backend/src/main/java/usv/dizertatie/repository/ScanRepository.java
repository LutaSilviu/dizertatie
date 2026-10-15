package usv.dizertatie.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import usv.dizertatie.domain.Scan;

public interface ScanRepository extends JpaRepository<Scan, Long> {

    Page<Scan> findAllByOrderByTimestampDesc(Pageable pageable);
}
