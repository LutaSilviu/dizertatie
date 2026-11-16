package usv.dizertatie.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import usv.dizertatie.domain.Scan;

public interface ScanRepository extends JpaRepository<Scan, Long> {

    Page<Scan> findAllByOrderByTimestampDesc(Pageable pageable);

    /**
     * Scans the user ran directly, newest first. Scans created by a benchmark run are referenced
     * from {@code benchmark_pages} and belong to that run's results, not to the scan history.
     */
    @Query(value = """
            SELECT s FROM Scan s
            WHERE NOT EXISTS (
                SELECT 1 FROM BenchmarkPage p
                WHERE p.axeScanId = s.id OR p.aiScanId = s.id OR p.combinedScanId = s.id)
            ORDER BY s.timestamp DESC
            """,
            countQuery = """
            SELECT COUNT(s) FROM Scan s
            WHERE NOT EXISTS (
                SELECT 1 FROM BenchmarkPage p
                WHERE p.axeScanId = s.id OR p.aiScanId = s.id OR p.combinedScanId = s.id)
            """)
    Page<Scan> findUserScansNewestFirst(Pageable pageable);
}
