package usv.dizertatie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import usv.dizertatie.domain.GroundTruthLabel;

import java.util.List;

public interface GroundTruthLabelRepository extends JpaRepository<GroundTruthLabel, Long> {

    List<GroundTruthLabel> findByUrl(String url);
}
