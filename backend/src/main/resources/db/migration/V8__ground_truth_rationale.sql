-- V8: Keep the dataset's rationale for each ground-truth label (why the element fails the criterion),
-- so false-negative examples in benchmark metrics can explain what was missed.
-- DatasetSeeder fills it for new rows and backfills existing seeded rows on the next start.
ALTER TABLE ground_truth_labels
    ADD COLUMN rationale TEXT;
