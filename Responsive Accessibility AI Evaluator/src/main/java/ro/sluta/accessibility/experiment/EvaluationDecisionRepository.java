package ro.sluta.accessibility.experiment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.sluta.accessibility.domain.DetectionClass;

@Component
public class EvaluationDecisionRepository {
    private final JdbcClient jdbc; private final ObjectMapper mapper;
    public EvaluationDecisionRepository(JdbcClient jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Transactional
    public EvaluationDecision save(EvaluationDecision value) {
        long duplicate = jdbc.sql("""
                select count(*) from evaluation_decision where run_id=:run and
                  ((finding_id is null and :finding is null) or finding_id=:finding) and
                  ((ground_truth_id is null and :truth is null) or ground_truth_id=:truth) and
                  detection_class=:class and reviewer=:reviewer and decision_kind=:kind
                """).param("run", value.runId()).param("finding", value.findingId().orElse(null), Types.OTHER)
                .param("truth", value.groundTruthId(), Types.VARCHAR).param("class", value.detectionClass().name())
                .param("reviewer", value.reviewer()).param("kind", value.decisionKind().name()).query(Long.class).single();
        if (duplicate > 0) throw new DuplicateKeyException("Evaluatorul a înregistrat deja această clasificare.");
        jdbc.sql("""
                insert into evaluation_decision(decision_id,run_id,finding_id,ground_truth_id,detection_class,
                  localization_score,wcag_score,e1,e2,e3,e4,e5,quality_score,reviewer,decision_kind,blind,
                  source_decision_ids_json,notes,created_at,version)
                values(:id,:run,:finding,:truth,:class,:localization,:wcag,:e1,:e2,:e3,:e4,:e5,:quality,:reviewer,
                  :kind,:blind,:sources,:notes,:created,0)
                """).param("id", value.decisionId()).param("run", value.runId())
                .param("finding", value.findingId().orElse(null), Types.OTHER).param("truth", value.groundTruthId(), Types.VARCHAR)
                .param("class", value.detectionClass().name()).param("localization", value.localizationScore(), Types.INTEGER)
                .param("wcag", value.wcagScore(), Types.INTEGER).param("e1", value.e1(), Types.INTEGER)
                .param("e2", value.e2(), Types.INTEGER).param("e3", value.e3(), Types.INTEGER)
                .param("e4", value.e4(), Types.INTEGER).param("e5", value.e5(), Types.INTEGER)
                .param("quality", value.qualityScore(), Types.NUMERIC).param("reviewer", value.reviewer())
                .param("kind", value.decisionKind().name()).param("blind", value.blind())
                .param("sources", json(value.sourceDecisionIds())).param("notes", value.notes(), Types.VARCHAR)
                .param("created", utc(value.createdAt())).update();
        return value;
    }

    public List<EvaluationDecision> byRun(UUID runId) {
        return jdbc.sql("select * from evaluation_decision where run_id=:run order by created_at,decision_id")
                .param("run", runId).query(this::map).list();
    }
    public List<EvaluationDecision> byPlan(UUID planId) {
        return jdbc.sql("""
                select d.* from evaluation_decision d join experiment_execution e on e.run_id=d.run_id
                where e.plan_id=:plan order by d.created_at,d.decision_id
                """).param("plan", planId).query(this::map).list();
    }
    public List<EvaluationDecision> recent(int limit) {
        return jdbc.sql("select * from evaluation_decision order by created_at desc,decision_id desc limit :limit")
                .param("limit", Math.max(1, Math.min(200, limit))).query(this::map).list();
    }

    private EvaluationDecision map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new EvaluationDecision(uuid(rs, "decision_id"), uuid(rs, "run_id"), Optional.ofNullable(uuidNullable(rs, "finding_id")),
                rs.getString("ground_truth_id"), DetectionClass.valueOf(rs.getString("detection_class")),
                integer(rs, "localization_score"), integer(rs, "wcag_score"), integer(rs, "e1"), integer(rs, "e2"),
                integer(rs, "e3"), integer(rs, "e4"), integer(rs, "e5"), doubleValue(rs, "quality_score"),
                rs.getString("reviewer"), EvaluationDecisionKind.valueOf(rs.getString("decision_kind")), rs.getBoolean("blind"),
                fromJson(rs.getString("source_decision_ids_json")), rs.getString("notes"), instant(rs, "created_at"));
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private List<UUID> fromJson(String value) { try { return mapper.readValue(value, new TypeReference<>() {}); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static OffsetDateTime utc(Instant value) { return value.truncatedTo(java.time.temporal.ChronoUnit.MICROS).atOffset(ZoneOffset.UTC); }
    private static Instant instant(java.sql.ResultSet rs, String name) throws java.sql.SQLException { return rs.getObject(name, OffsetDateTime.class).toInstant(); }
    private static UUID uuid(java.sql.ResultSet rs, String name) throws java.sql.SQLException { return rs.getObject(name, UUID.class); }
    private static UUID uuidNullable(java.sql.ResultSet rs, String name) throws java.sql.SQLException { return rs.getObject(name) == null ? null : rs.getObject(name, UUID.class); }
    private static Integer integer(java.sql.ResultSet rs, String name) throws java.sql.SQLException { int value=rs.getInt(name); return rs.wasNull()?null:value; }
    private static Double doubleValue(java.sql.ResultSet rs, String name) throws java.sql.SQLException { double value=rs.getDouble(name); return rs.wasNull()?null:value; }
}
