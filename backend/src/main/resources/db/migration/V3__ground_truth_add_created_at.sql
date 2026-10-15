-- V3: Add created_at column to ground_truth_labels

ALTER TABLE ground_truth_labels
    ADD COLUMN created_at TIMESTAMP;
