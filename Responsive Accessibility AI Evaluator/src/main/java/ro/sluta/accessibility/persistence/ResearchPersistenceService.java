package ro.sluta.accessibility.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.sluta.accessibility.domain.*;

@Service
public class ResearchPersistenceService {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final ArtifactIntegrityService integrity;

    public ResearchPersistenceService(JdbcClient jdbc, ObjectMapper mapper, ArtifactIntegrityService integrity) {
        this.jdbc = jdbc; this.mapper = mapper; this.integrity = integrity;
    }

    @Transactional
    public void saveAnalysis(AnalysisRecord value) {
        jdbc.sql("""
                insert into analysis_record(analysis_id,parent_analysis_id,requested_url,configuration_json,status,
                  progress_percent,created_at,updated_at,version)
                values(:id,:parent,:url,:config,:status,:progress,:created,:updated,:version)
                """).param("id", value.analysisId()).param("parent", value.parentAnalysisId(), Types.OTHER)
                .param("url", value.requestedUrl()).param("config", value.configurationJson())
                .param("status", value.status().name()).param("progress", value.progressPercent())
                .param("created", utc(value.createdAt())).param("updated", utc(value.updatedAt()))
                .param("version", value.version()).update();
    }

    @Transactional
    public void saveRun(RunRecord value) {
        jdbc.sql("""
                insert into analysis_run(run_id,analysis_id,method,model_id,condition_name,repetition,status,started_at,
                  completed_at,latency_ms,input_tokens,cached_input_tokens,output_tokens,cost_usd,schema_valid,error_code,
                  error_message,raw_result_ref,version)
                values(:id,:analysis,:method,:model,:condition,:repetition,:status,:started,:completed,:latency,:input,
                  :cached,:output,:cost,:schemaValid,:errorCode,:errorMessage,:rawRef,:version)
                """).param("id", value.runId()).param("analysis", value.analysisId()).param("method", value.method().name())
                .param("model", value.modelId(), Types.VARCHAR).param("condition", value.condition(), Types.VARCHAR)
                .param("repetition", value.repetition()).param("status", value.status().name())
                .param("started", utc(value.startedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("completed", utc(value.completedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("latency", value.latencyMs(), Types.BIGINT).param("input", value.inputTokens(), Types.BIGINT)
                .param("cached", value.cachedInputTokens(), Types.BIGINT).param("output", value.outputTokens(), Types.BIGINT)
                .param("cost", value.costUsd(), Types.NUMERIC).param("schemaValid", value.schemaValid(), Types.BOOLEAN)
                .param("errorCode", value.errorCode(), Types.VARCHAR).param("errorMessage", value.errorMessage(), Types.VARCHAR)
                .param("rawRef", value.rawResultRef(), Types.VARCHAR).param("version", value.version()).update();
    }

    @Transactional
    public void saveSnapshot(PageSnapshot value) {
        jdbc.sql("""
                insert into page_snapshot(snapshot_id,run_id,requested_url,final_url,viewport,browser_name,browser_version,
                  content_hash,captured_at,status,metadata_json,version)
                values(:id,:run,:requested,:finalUrl,:viewport,:browser,:browserVersion,:hash,:captured,:status,:metadata,0)
                """).param("id", value.snapshotId()).param("run", value.runId())
                .param("requested", value.requestedUrl().toString()).param("finalUrl", text(value.finalUrl()), Types.VARCHAR)
                .param("viewport", value.viewport().name())
                .param("browser", value.browser() == null ? null : value.browser().browserName(), Types.VARCHAR)
                .param("browserVersion", value.browser() == null ? null : value.browser().browserVersion(), Types.VARCHAR)
                .param("hash", value.contentHash(), Types.VARCHAR).param("captured", utc(value.capturedAt()))
                .param("status", value.status().name()).param("metadata", json(value)).update();
    }

    @Transactional
    public void saveArtifact(StoredArtifact value) {
        integrity.verify(value.reference());
        jdbc.sql("""
                insert into artifact(artifact_id,run_id,snapshot_id,artifact_type,relative_path,mime_type,byte_size,sha256,
                  producer_version,created_at)
                values(:id,:run,:snapshot,:type,:path,:mime,:size,:hash,:producer,:created)
                """).param("id", value.artifactId()).param("run", value.runId())
                .param("snapshot", value.snapshotId(), Types.OTHER).param("type", value.reference().artifactType().name())
                .param("path", value.reference().relativePath()).param("mime", value.reference().mimeType())
                .param("size", value.reference().byteSize()).param("hash", value.reference().sha256())
                .param("producer", value.reference().extractorVersion()).param("created", utc(value.createdAt())).update();
    }

    @Transactional
    public void saveRawResult(RawResultRecord value) {
        jdbc.sql("""
                insert into raw_result(raw_result_id,run_id,component,artifact_id,metadata_artifact_id,
                  provider_request_id,engine_version,created_at)
                values(:id,:run,:component,:artifact,:metadata,:requestId,:engine,:created)
                """).param("id", value.rawResultId()).param("run", value.runId())
                .param("component", value.component()).param("artifact", value.artifactId())
                .param("metadata", value.metadataArtifactId(), Types.OTHER)
                .param("requestId", value.providerRequestId(), Types.VARCHAR)
                .param("engine", value.engineVersion(), Types.VARCHAR)
                .param("created", utc(value.createdAt())).update();
    }

    @Transactional
    public void saveNormalizationBatch(NormalizationBatchRecord value) {
        jdbc.sql("""
                insert into normalization_batch(batch_id,analysis_id,normalizer_version,matcher_version,configuration_json,
                  input_hash,result_hash,hybrid,created_at)
                values(:id,:analysis,:normalizer,:matcher,:config,:inputHash,:resultHash,:hybrid,:created)
                """).param("id", value.batchId()).param("analysis", value.analysisId())
                .param("normalizer", value.normalizerVersion()).param("matcher", value.matcherVersion(), Types.VARCHAR)
                .param("config", value.configurationJson()).param("inputHash", value.inputHash())
                .param("resultHash", value.resultHash()).param("hybrid", value.hybrid())
                .param("created", utc(value.createdAt())).update();
    }

    @Transactional
    public void saveFinding(UUID batchId, PredictedFinding value) {
        jdbc.sql("""
                insert into predicted_finding(finding_id,batch_id,run_id,snapshot_id,viewport,source,title,description,
                  primary_wcag_criterion,wcag_criteria,severity,axe_severity,ai_severity,display_severity,conformance_level,
                  normalized_category,element_fingerprint,location_json,explanation_json,impact,recommendation,confidence,
                  raw_reference,finding_key,duplicate_count,match_evidence_json,payload_json,normalizer_version)
                values(:id,:batch,:run,:snapshot,:viewport,:source,:title,:description,:primaryWcag,:wcag,:severity,
                  :axeSeverity,:aiSeverity,:displaySeverity,:level,:category,:fingerprint,:location,:explanations,:impact,
                  :recommendation,:confidence,:rawRef,:findingKey,:duplicates,:matchEvidence,:payload,:normalizer)
                """).param("id", value.findingId()).param("batch", batchId).param("run", value.runId())
                .param("snapshot", value.snapshotId()).param("viewport", value.viewport().name())
                .param("source", value.source().name()).param("title", value.title())
                .param("description", value.description(), Types.VARCHAR)
                .param("primaryWcag", value.primaryWcagCriterion(), Types.VARCHAR)
                .param("wcag", String.join(",", value.wcagCriteria())).param("severity", value.severity().name())
                .param("axeSeverity", name(value.axeSeverity()), Types.VARCHAR)
                .param("aiSeverity", name(value.aiSeverity()), Types.VARCHAR)
                .param("displaySeverity", value.displaySeverity().name()).param("level", value.conformanceLevel().name())
                .param("category", value.normalizedCategory()).param("fingerprint", value.elementFingerprint())
                .param("location", json(value.location()), Types.VARCHAR).param("explanations", json(value.explanations()))
                .param("impact", value.impact(), Types.VARCHAR).param("recommendation", value.recommendation(), Types.VARCHAR)
                .param("confidence", value.confidence(), Types.NUMERIC).param("rawRef", value.rawReference(), Types.VARCHAR)
                .param("findingKey", value.findingKey()).param("duplicates", value.duplicateCount())
                .param("matchEvidence", json(value.matchEvidence()), Types.VARCHAR).param("payload", json(value))
                .param("normalizer", value.normalizerVersion()).update();
        for (FindingSourceReference ref : value.sourceRefs()) {
            jdbc.sql("""
                    insert into finding_source_ref(source_ref_id,finding_id,source,source_id,raw_reference,item_index)
                    values(:id,:finding,:source,:sourceId,:rawRef,:itemIndex)
                    """).param("id", UUID.randomUUID()).param("finding", value.findingId())
                    .param("source", ref.source().name()).param("sourceId", ref.sourceId(), Types.VARCHAR)
                    .param("rawRef", ref.rawReference(), Types.VARCHAR).param("itemIndex", ref.itemIndex()).update();
        }
    }

    @Transactional
    public void saveExperimentConfiguration(ExperimentConfigurationRecord value) {
        jdbc.sql("""
                insert into experiment_configuration(configuration_id,name,configuration_version,configuration_json,
                  configuration_hash,frozen_at,version) values(:id,:name,:configVersion,:json,:hash,:frozen,0)
                """).param("id", value.configurationId()).param("name", value.name())
                .param("configVersion", value.configurationVersion()).param("json", value.configurationJson())
                .param("hash", value.configurationHash()).param("frozen", utc(value.frozenAt())).update();
    }

    @Transactional
    public void saveGroundTruth(GroundTruthIssue value) {
        jdbc.sql("""
                insert into ground_truth_issue(ground_truth_id,scenario_id,variant,viewport,state_id,target_element,barrier,
                  wcag_criterion,conformance_level,expected_presence,manual_evidence,fixture_version,fixture_hash,payload_json,version)
                values(:id,:scenario,:variant,:viewport,:stateId,:target,:barrier,:wcag,:level,:expected,:evidence,
                  :fixtureVersion,:fixtureHash,:payload,0)
                """).param("id", value.groundTruthId()).param("scenario", value.scenarioId()).param("variant", value.variant())
                .param("viewport", value.viewport().name()).param("stateId", value.stateId())
                .param("target", value.targetElement(), Types.VARCHAR).param("barrier", value.barrier())
                .param("wcag", value.wcagCriterion()).param("level", value.conformanceLevel())
                .param("expected", value.expected().name()).param("evidence", value.manualEvidence(), Types.VARCHAR)
                .param("fixtureVersion", value.fixtureVersion()).param("fixtureHash", value.fixtureHash())
                .param("payload", json(value)).update();
    }

    @Transactional
    public void saveEvaluation(ExperimentEvaluation value) {
        Long duplicate = jdbc.sql("""
                select count(*) from experiment_evaluation where run_id=:run
                  and ((finding_id=:finding) or (finding_id is null and :finding is null))
                  and ((ground_truth_id=:groundTruth) or (ground_truth_id is null and :groundTruth is null))
                  and detection_class=:class and reviewer=:reviewer
                """).param("run", value.runId()).param("finding", value.findingId().orElse(null), Types.OTHER)
                .param("groundTruth", value.matchedGroundTruthId(), Types.VARCHAR)
                .param("class", value.detectionClass().name()).param("reviewer", value.reviewer())
                .query(Long.class).single();
        if (duplicate > 0) throw new org.springframework.dao.DuplicateKeyException("Evaluarea există deja.");
        jdbc.sql("""
                insert into experiment_evaluation(evaluation_id,run_id,finding_id,ground_truth_id,detection_class,
                  localization_score,wcag_score,e1,e2,e3,e4,e5,quality_score,reviewer,notes,payload_json,created_at,version)
                values(:id,:run,:finding,:groundTruth,:class,:localization,:wcag,:e1,:e2,:e3,:e4,:e5,:quality,
                  :reviewer,:notes,:payload,:created,0)
                """).param("id", value.evaluationId()).param("run", value.runId())
                .param("finding", value.findingId().orElse(null), Types.OTHER)
                .param("groundTruth", value.matchedGroundTruthId(), Types.VARCHAR)
                .param("class", value.detectionClass().name()).param("localization", value.localizationScore(), Types.INTEGER)
                .param("wcag", value.wcagScore(), Types.INTEGER).param("e1", value.e1(), Types.INTEGER)
                .param("e2", value.e2(), Types.INTEGER).param("e3", value.e3(), Types.INTEGER)
                .param("e4", value.e4(), Types.INTEGER).param("e5", value.e5(), Types.INTEGER)
                .param("quality", value.qualityScore(), Types.NUMERIC).param("reviewer", value.reviewer())
                .param("notes", value.notes(), Types.VARCHAR).param("payload", json(value))
                .param("created", utc(Instant.now())).update();
    }

    public Optional<AnalysisRecord> findAnalysis(UUID id) {
        return jdbc.sql("select * from analysis_record where analysis_id=:id").param("id", id).query(this::analysis).optional();
    }

    @Transactional
    public AnalysisRecord updateAnalysisStatus(UUID id, long expectedVersion, AnalysisStatus status, int progress, Instant now) {
        int changed = jdbc.sql("""
                update analysis_record set status=:status,progress_percent=:progress,updated_at=:updated,version=version+1
                where analysis_id=:id and version=:version
                """).param("status", status.name()).param("progress", progress).param("updated", utc(now))
                .param("id", id).param("version", expectedVersion).update();
        if (changed != 1) throw new org.springframework.dao.OptimisticLockingFailureException("Analiza a fost modificată concurent.");
        return findAnalysis(id).orElseThrow();
    }

    public ResearchBundle loadBundle(UUID analysisId) {
        AnalysisRecord analysis = findAnalysis(analysisId).orElseThrow();
        List<RunRecord> runs = jdbc.sql("select * from analysis_run where analysis_id=:id order by repetition,run_id")
                .param("id", analysisId).query(this::run).list();
        List<PageSnapshot> snapshots = new ArrayList<>(); List<StoredArtifact> artifacts = new ArrayList<>();
        List<RawResultRecord> rawResults = new ArrayList<>();
        List<PredictedFinding> findings = new ArrayList<>(); List<ExperimentEvaluation> evaluations = new ArrayList<>();
        for (RunRecord run : runs) {
            snapshots.addAll(jdbc.sql("select metadata_json from page_snapshot where run_id=:id order by snapshot_id")
                    .param("id", run.runId()).query((rs, row) -> fromJson(rs.getString(1), PageSnapshot.class)).list());
            artifacts.addAll(jdbc.sql("select * from artifact where run_id=:id order by relative_path")
                    .param("id", run.runId()).query(this::artifact).list());
            rawResults.addAll(jdbc.sql("select * from raw_result where run_id=:id order by component")
                    .param("id", run.runId()).query(this::rawResult).list());
            findings.addAll(jdbc.sql("select payload_json from predicted_finding where run_id=:id order by finding_key")
                    .param("id", run.runId()).query((rs, row) -> fromJson(rs.getString(1), PredictedFinding.class)).list());
            evaluations.addAll(jdbc.sql("select payload_json from experiment_evaluation where run_id=:id order by evaluation_id")
                    .param("id", run.runId()).query((rs, row) -> fromJson(rs.getString(1), ExperimentEvaluation.class)).list());
        }
        List<NormalizationBatchRecord> batches = jdbc.sql("select * from normalization_batch where analysis_id=:id order by created_at")
                .param("id", analysisId).query(this::batch).list();
        List<ExperimentConfigurationRecord> configs = jdbc.sql("select * from experiment_configuration order by frozen_at")
                .query(this::configuration).list();
        List<GroundTruthIssue> truth = jdbc.sql("select payload_json from ground_truth_issue order by ground_truth_id")
                .query((rs, row) -> fromJson(rs.getString(1), GroundTruthIssue.class)).list();
        return new ResearchBundle(analysis, runs, snapshots, artifacts, rawResults, batches, findings, configs, truth, evaluations);
    }

    public HistoryPage history(HistoryFilter filter) {
        StringBuilder where = new StringBuilder(" where 1=1");
        var params = new java.util.LinkedHashMap<String, Object>();
        if (filter.from() != null) { where.append(" and a.created_at>=:fromDate"); params.put("fromDate", utc(filter.from())); }
        if (filter.to() != null) { where.append(" and a.created_at<=:toDate"); params.put("toDate", utc(filter.to())); }
        if (filter.urlContains() != null && !filter.urlContains().isBlank()) { where.append(" and lower(a.requested_url) like :url"); params.put("url", "%" + filter.urlContains().toLowerCase() + "%"); }
        if (filter.status() != null) { where.append(" and a.status=:status"); params.put("status", filter.status().name()); }
        if (filter.method() != null) { where.append(" and exists(select 1 from analysis_run r where r.analysis_id=a.analysis_id and r.method=:method)"); params.put("method", filter.method().name()); }
        if (filter.modelId() != null && !filter.modelId().isBlank()) { where.append(" and exists(select 1 from analysis_run r where r.analysis_id=a.analysis_id and r.model_id=:model)"); params.put("model", filter.modelId()); }
        var countSpec = jdbc.sql("select count(*) from analysis_record a" + where); params.forEach(countSpec::param);
        long total = countSpec.query(Long.class).single();
        String order = filter.ascending() ? " asc" : " desc";
        var dataSpec = jdbc.sql("select a.* from analysis_record a" + where + " order by a.created_at" + order + ",a.analysis_id" + order + " limit :limit offset :offset");
        params.forEach(dataSpec::param); dataSpec.param("limit", filter.size()).param("offset", filter.page() * filter.size());
        return new HistoryPage(dataSpec.query(this::analysis).list(), total, filter.page(), filter.size());
    }

    @Transactional
    public AnalysisRecord repeat(UUID sourceAnalysisId, Instant now) {
        ResearchBundle source = loadBundle(sourceAnalysisId);
        UUID newAnalysisId = UUID.randomUUID();
        AnalysisRecord repeated = new AnalysisRecord(newAnalysisId, sourceAnalysisId, source.analysis().requestedUrl(),
                source.analysis().configurationJson(), AnalysisStatus.CREATED, 0, now, now, 0);
        saveAnalysis(repeated);
        for (RunRecord run : source.runs()) saveRun(new RunRecord(UUID.randomUUID(), newAnalysisId, run.method(), run.modelId(),
                run.condition(), run.repetition(), RunStatus.PENDING, null, null, null, null, null, null,
                null, null, null, null, null, 0));
        return repeated;
    }

    @Transactional
    public int markAbandonedRuns() {
        return jdbc.sql("""
                update analysis_run set status='ABANDONED',error_code='APP_RESTARTED',
                  error_message='Rularea RUNNING a fost abandonată la restart.',version=version+1
                where status='RUNNING'
                """).update();
    }

    private AnalysisRecord analysis(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new AnalysisRecord(uuid(rs, "analysis_id"), uuidNullable(rs, "parent_analysis_id"), rs.getString("requested_url"),
                rs.getString("configuration_json"), AnalysisStatus.valueOf(rs.getString("status")), rs.getInt("progress_percent"),
                instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("version"));
    }
    private RunRecord run(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new RunRecord(uuid(rs, "run_id"), uuid(rs, "analysis_id"), AnalysisMethod.valueOf(rs.getString("method")),
                rs.getString("model_id"), rs.getString("condition_name"), rs.getInt("repetition"),
                RunStatus.valueOf(rs.getString("status")), instantNullable(rs, "started_at"), instantNullable(rs, "completed_at"),
                number(rs, "latency_ms"), number(rs, "input_tokens"), number(rs, "cached_input_tokens"), number(rs, "output_tokens"),
                decimal(rs, "cost_usd"), bool(rs, "schema_valid"), rs.getString("error_code"), rs.getString("error_message"),
                rs.getString("raw_result_ref"), rs.getLong("version"));
    }
    private StoredArtifact artifact(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        var ref = new ArtifactReference(ArtifactType.valueOf(rs.getString("artifact_type")), rs.getString("relative_path"),
                rs.getString("mime_type"), rs.getLong("byte_size"), rs.getString("sha256"), rs.getString("producer_version"));
        return new StoredArtifact(uuid(rs, "artifact_id"), uuid(rs, "run_id"), uuidNullable(rs, "snapshot_id"), ref, instant(rs, "created_at"));
    }
    private RawResultRecord rawResult(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new RawResultRecord(uuid(rs, "raw_result_id"), uuid(rs, "run_id"), rs.getString("component"),
                uuid(rs, "artifact_id"), uuidNullable(rs, "metadata_artifact_id"),
                rs.getString("provider_request_id"), rs.getString("engine_version"), instant(rs, "created_at"));
    }
    private NormalizationBatchRecord batch(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new NormalizationBatchRecord(uuid(rs, "batch_id"), uuid(rs, "analysis_id"), rs.getString("normalizer_version"),
                rs.getString("matcher_version"), rs.getString("configuration_json"), rs.getString("input_hash"),
                rs.getString("result_hash"), rs.getBoolean("hybrid"), instant(rs, "created_at"));
    }
    private ExperimentConfigurationRecord configuration(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ExperimentConfigurationRecord(uuid(rs, "configuration_id"), rs.getString("name"),
                rs.getString("configuration_version"), rs.getString("configuration_json"), rs.getString("configuration_hash"),
                instant(rs, "frozen_at"), rs.getLong("version"));
    }

    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("Serializare JSON eșuată.", e); } }
    private <T> T fromJson(String value, Class<T> type) { try { return mapper.readValue(value, type); } catch (Exception e) { throw new IllegalStateException("Deserializare JSON eșuată.", e); } }
    private static OffsetDateTime utc(Instant value) {
        return value == null ? null : value.truncatedTo(java.time.temporal.ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
    private static String text(Object value) { return value == null ? null : value.toString(); }
    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
    private static UUID uuid(java.sql.ResultSet rs, String name) throws java.sql.SQLException { return rs.getObject(name, UUID.class); }
    private static UUID uuidNullable(java.sql.ResultSet rs, String name) throws java.sql.SQLException { return rs.getObject(name) == null ? null : rs.getObject(name, UUID.class); }
    private static Instant instant(java.sql.ResultSet rs, String name) throws java.sql.SQLException { return rs.getObject(name, OffsetDateTime.class).toInstant(); }
    private static Instant instantNullable(java.sql.ResultSet rs, String name) throws java.sql.SQLException { return rs.getObject(name) == null ? null : instant(rs, name); }
    private static Long number(java.sql.ResultSet rs, String name) throws java.sql.SQLException { long value = rs.getLong(name); return rs.wasNull() ? null : value; }
    private static BigDecimal decimal(java.sql.ResultSet rs, String name) throws java.sql.SQLException {
        BigDecimal value = rs.getBigDecimal(name);
        return value == null ? null : value.stripTrailingZeros();
    }
    private static Boolean bool(java.sql.ResultSet rs, String name) throws java.sql.SQLException { boolean value = rs.getBoolean(name); return rs.wasNull() ? null : value; }
}
