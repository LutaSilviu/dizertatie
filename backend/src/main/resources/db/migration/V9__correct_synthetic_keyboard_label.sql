-- Case 14 now demonstrates a pointer-only control rather than an unmarked heading.
UPDATE ground_truth_labels old_label
SET wcag_criterion = '2.1.1'
WHERE old_label.url = 'http://localhost:8080/fixtures/synthetic/case-14.html'
  AND old_label.wcag_criterion = '1.3.1'
  AND old_label.element_selector = '#defect'
  AND old_label.annotator = 'project-synthetic'
  AND NOT EXISTS (
      SELECT 1 FROM ground_truth_labels corrected
      WHERE corrected.url = old_label.url
        AND corrected.wcag_criterion = '2.1.1'
        AND corrected.element_selector = old_label.element_selector
  );
