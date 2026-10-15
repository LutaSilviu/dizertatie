-- V3: Add scan ID reference columns to benchmark_pages

ALTER TABLE benchmark_pages
    ADD COLUMN IF NOT EXISTS axe_scan_id     BIGINT,
    ADD COLUMN IF NOT EXISTS ai_scan_id      BIGINT,
    ADD COLUMN IF NOT EXISTS combined_scan_id BIGINT;
