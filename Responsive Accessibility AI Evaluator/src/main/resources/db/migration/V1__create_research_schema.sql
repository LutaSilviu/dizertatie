create table analysis_record (
    analysis_id uuid primary key,
    parent_analysis_id uuid null,
    requested_url varchar(2048) not null,
    configuration_json text not null,
    status varchar(40) not null,
    progress_percent integer not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint fk_analysis_parent foreign key (parent_analysis_id) references analysis_record(analysis_id),
    constraint ck_analysis_progress check (progress_percent between 0 and 100)
);

create table analysis_run (
    run_id uuid primary key,
    analysis_id uuid not null,
    method varchar(40) not null,
    model_id varchar(120) null,
    condition_name varchar(80) null,
    repetition integer not null,
    status varchar(40) not null,
    started_at timestamp with time zone null,
    completed_at timestamp with time zone null,
    latency_ms bigint null,
    input_tokens bigint null,
    cached_input_tokens bigint null,
    output_tokens bigint null,
    cost_usd numeric(20,10) null,
    schema_valid boolean null,
    error_code varchar(80) null,
    error_message varchar(4000) null,
    raw_result_ref varchar(2048) null,
    version bigint not null default 0,
    constraint fk_run_analysis foreign key (analysis_id) references analysis_record(analysis_id),
    constraint uq_run_condition unique (analysis_id, method, model_id, condition_name, repetition)
);

create table page_snapshot (
    snapshot_id uuid primary key,
    run_id uuid not null,
    requested_url varchar(2048) not null,
    final_url varchar(2048) null,
    viewport varchar(40) not null,
    browser_name varchar(120) null,
    browser_version varchar(120) null,
    content_hash varchar(64) null,
    captured_at timestamp with time zone not null,
    status varchar(40) not null,
    metadata_json text null,
    version bigint not null default 0,
    constraint fk_snapshot_run foreign key (run_id) references analysis_run(run_id),
    constraint uq_snapshot_run_viewport unique (run_id, viewport)
);

create table artifact (
    artifact_id uuid primary key,
    run_id uuid not null,
    snapshot_id uuid null,
    artifact_type varchar(80) not null,
    relative_path varchar(2048) not null,
    mime_type varchar(255) not null,
    byte_size bigint not null,
    sha256 varchar(64) not null,
    producer_version varchar(120) not null,
    created_at timestamp with time zone not null,
    constraint fk_artifact_run foreign key (run_id) references analysis_run(run_id),
    constraint fk_artifact_snapshot foreign key (snapshot_id) references page_snapshot(snapshot_id),
    constraint uq_artifact_path unique (relative_path),
    constraint uq_artifact_snapshot_type unique (snapshot_id, artifact_type, relative_path),
    constraint ck_artifact_hash check (length(sha256) = 64),
    constraint ck_artifact_size check (byte_size >= 0)
);

create table raw_result (
    raw_result_id uuid primary key,
    run_id uuid not null,
    component varchar(40) not null,
    artifact_id uuid not null,
    metadata_artifact_id uuid null,
    provider_request_id varchar(255) null,
    engine_version varchar(120) null,
    created_at timestamp with time zone not null,
    constraint fk_raw_run foreign key (run_id) references analysis_run(run_id),
    constraint fk_raw_artifact foreign key (artifact_id) references artifact(artifact_id),
    constraint fk_raw_metadata foreign key (metadata_artifact_id) references artifact(artifact_id),
    constraint uq_raw_component unique (run_id, component)
);

create table normalization_batch (
    batch_id uuid primary key,
    analysis_id uuid not null,
    normalizer_version varchar(120) not null,
    matcher_version varchar(120) null,
    configuration_json text not null,
    input_hash varchar(64) not null,
    result_hash varchar(64) not null,
    hybrid boolean not null,
    created_at timestamp with time zone not null,
    constraint fk_batch_analysis foreign key (analysis_id) references analysis_record(analysis_id),
    constraint uq_batch_hash unique (analysis_id, normalizer_version, matcher_version, input_hash)
);

create table predicted_finding (
    finding_id uuid primary key,
    batch_id uuid not null,
    run_id uuid not null,
    snapshot_id uuid not null,
    viewport varchar(40) not null,
    source varchar(40) not null,
    title varchar(1000) not null,
    description text null,
    primary_wcag_criterion varchar(40) null,
    wcag_criteria varchar(1000) not null,
    severity varchar(40) not null,
    axe_severity varchar(40) null,
    ai_severity varchar(40) null,
    display_severity varchar(40) not null,
    conformance_level varchar(40) not null,
    normalized_category varchar(500) not null,
    element_fingerprint varchar(64) not null,
    location_json text null,
    explanation_json text null,
    impact text null,
    recommendation text null,
    confidence numeric(6,5) null,
    raw_reference varchar(2048) null,
    finding_key varchar(64) not null,
    duplicate_count integer not null,
    match_evidence_json text null,
    payload_json text not null,
    normalizer_version varchar(120) not null,
    constraint fk_finding_batch foreign key (batch_id) references normalization_batch(batch_id),
    constraint fk_finding_run foreign key (run_id) references analysis_run(run_id),
    constraint fk_finding_snapshot foreign key (snapshot_id) references page_snapshot(snapshot_id),
    constraint uq_finding_batch_key unique (batch_id, finding_key),
    constraint ck_finding_confidence check (confidence is null or (confidence >= 0 and confidence <= 1))
);

create table finding_source_ref (
    source_ref_id uuid primary key,
    finding_id uuid not null,
    source varchar(40) not null,
    source_id varchar(255) null,
    raw_reference varchar(2048) null,
    item_index integer not null,
    constraint fk_source_ref_finding foreign key (finding_id) references predicted_finding(finding_id),
    constraint uq_source_ref unique (finding_id, source, source_id, item_index)
);

create table experiment_configuration (
    configuration_id uuid primary key,
    name varchar(255) not null,
    configuration_version varchar(120) not null,
    configuration_json text not null,
    configuration_hash varchar(64) not null,
    frozen_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint uq_experiment_config unique (name, configuration_version),
    constraint uq_experiment_hash unique (configuration_hash)
);

create table ground_truth_issue (
    ground_truth_id varchar(120) primary key,
    scenario_id varchar(40) not null,
    variant varchar(20) not null,
    viewport varchar(40) not null,
    state_id varchar(120) not null,
    target_element text null,
    barrier text not null,
    wcag_criterion varchar(40) not null,
    conformance_level varchar(40) not null,
    expected_presence varchar(40) not null,
    manual_evidence text null,
    fixture_version varchar(120) not null,
    fixture_hash varchar(64) not null,
    payload_json text not null,
    version bigint not null default 0,
    constraint uq_ground_truth unique (scenario_id, variant, viewport, state_id, wcag_criterion, fixture_version)
);

create table experiment_evaluation (
    evaluation_id uuid primary key,
    run_id uuid not null,
    finding_id uuid null,
    ground_truth_id varchar(120) null,
    detection_class varchar(40) not null,
    localization_score integer null,
    wcag_score integer null,
    e1 integer null,
    e2 integer null,
    e3 integer null,
    e4 integer null,
    e5 integer null,
    quality_score numeric(8,5) null,
    reviewer varchar(255) not null,
    notes text null,
    payload_json text not null,
    created_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint fk_evaluation_run foreign key (run_id) references analysis_run(run_id),
    constraint fk_evaluation_finding foreign key (finding_id) references predicted_finding(finding_id),
    constraint fk_evaluation_ground_truth foreign key (ground_truth_id) references ground_truth_issue(ground_truth_id),
    constraint uq_evaluation unique (run_id, finding_id, ground_truth_id, detection_class, reviewer)
);

create index idx_analysis_created on analysis_record(created_at);
create index idx_analysis_url on analysis_record(requested_url);
create index idx_run_analysis on analysis_run(analysis_id);
create index idx_run_filters on analysis_run(method, model_id, status);
create index idx_snapshot_run on page_snapshot(run_id);
create index idx_finding_run on predicted_finding(run_id);
create index idx_evaluation_run on experiment_evaluation(run_id);
