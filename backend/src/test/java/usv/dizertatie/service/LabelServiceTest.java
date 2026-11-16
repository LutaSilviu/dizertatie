package usv.dizertatie.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import usv.dizertatie.controller.LabelRequest;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabelServiceTest {

    @Mock
    private GroundTruthLabelRepository repository;

    @InjectMocks
    private LabelService service;

    @Test
    void create_withoutUrlOrCriterion_isRejectedWith400() {
        for (LabelRequest bad : new LabelRequest[]{
                new LabelRequest(null, "1.1.1", "A", "critical", "img", "me", null, 1.0),
                new LabelRequest("https://a.example", " ", "A", "critical", "img", "me", null, 1.0)}) {
            assertThatThrownBy(() -> service.create(bad))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void create_setsCreatedAt() {
        when(repository.save(any(GroundTruthLabel.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new LabelRequest("https://a.example", "1.1.1", "A", "critical", "img", "me", null, 1.0));

        verify(repository).save(argThat(l -> l.getCreatedAt() != null));
    }
}
