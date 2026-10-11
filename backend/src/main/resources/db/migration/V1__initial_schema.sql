-- V1: Initial schema for Web Accessibility Analyzer

CREATE TABLE scans (
    id                BIGSERIAL PRIMARY KEY,
    url               VARCHAR(2048) NOT NULL,
    method            VARCHAR(50)   NOT NULL,
    model_id          VARCHAR(255),
    prompt_version    VARCHAR(50),
    axe_version       VARCHAR(50),
    browser_version   VARCHAR(100),
    cache_hit         BOOLEAN,
    raw_ai_response   TEXT,
    timestamp         TIMESTAMP     NOT NULL
);

CREATE TABLE findings (
    id               BIGSERIAL PRIMARY KEY,
    scan_id          BIGINT        NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    source_method    VARCHAR(50),
    wcag_criterion   VARCHAR(20),
    wcag_level       VARCHAR(5),
    severity         VARCHAR(20),
    element_selector TEXT,
    description      TEXT,
    suggestion       TEXT,
    confidence       DOUBLE PRECISION
);

CREATE TABLE benchmark_runs (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    total_pages  INT          NOT NULL DEFAULT 0,
    pages_done   INT          NOT NULL DEFAULT 0
);

CREATE TABLE benchmark_pages (
    id                   BIGSERIAL PRIMARY KEY,
    run_id               BIGINT        NOT NULL REFERENCES benchmark_runs(id) ON DELETE CASCADE,
    url                  VARCHAR(2048) NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    latency_axe_ms       BIGINT,
    latency_ai_ms        BIGINT,
    latency_combined_ms  BIGINT,
    estimated_cost_usd   DOUBLE PRECISION
);

CREATE TABLE ground_truth_labels (
    id               BIGSERIAL PRIMARY KEY,
    url              VARCHAR(2048) NOT NULL,
    wcag_criterion   VARCHAR(20),
    wcag_level       VARCHAR(5),
    severity         VARCHAR(20),
    element_selector TEXT,
    annotator        VARCHAR(255),
    verified_by      VARCHAR(255),
    confidence       DOUBLE PRECISION
);
