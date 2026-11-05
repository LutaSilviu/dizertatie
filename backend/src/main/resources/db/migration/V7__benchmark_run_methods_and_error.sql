-- V7: Benchmark runs record which scan methods were requested and a summary of what failed.
-- methods: comma-separated subset of AXE,AI,COMBINED (NULL for runs created before V7 = all three).
-- error_summary: human-readable failure summary, e.g. "AI failed on 2/2 pages: ..."; NULL when nothing failed.
ALTER TABLE benchmark_runs
    ADD COLUMN methods       VARCHAR(100),
    ADD COLUMN error_summary TEXT;
