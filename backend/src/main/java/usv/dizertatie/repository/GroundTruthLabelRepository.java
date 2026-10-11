package usv.dizertatie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import usv.dizertatie.domain.GroundTruthLabel;

public interface GroundTruthLabelRepository extends JpaRepository<GroundTruthLabel, Long> {
}
