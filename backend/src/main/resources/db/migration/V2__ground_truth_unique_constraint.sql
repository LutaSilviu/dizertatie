-- V2: Add unique constraint on ground_truth_labels (url, wcag_criterion, element_selector)

ALTER TABLE ground_truth_labels
    ADD CONSTRAINT uq_ground_truth_labels_url_criterion_selector
        UNIQUE (url, wcag_criterion, element_selector);
