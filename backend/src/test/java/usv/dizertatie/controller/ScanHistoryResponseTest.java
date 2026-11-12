package usv.dizertatie.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanHistoryResponseTest {
    @Test
    void responseIncludesPaginationFieldsUsedByHistory() throws Exception {
        var json = new ObjectMapper().valueToTree(new ScanHistoryResponse(List.of(), 1, 20, 45));

        assertThat(json.path("number").asInt(-1)).isEqualTo(1);
        assertThat(json.path("totalPages").asInt(-1)).isEqualTo(3);
    }
}
