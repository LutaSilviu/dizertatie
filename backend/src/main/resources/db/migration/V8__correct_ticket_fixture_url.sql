-- Preserve pre-existing labels when the corrected key already exists.
UPDATE ground_truth_labels old_label
SET url = 'http://localhost:8080/fixtures/wai-bad/before/tickets.html'
WHERE old_label.url = 'http://localhost:8080/fixtures/wai-bad/before/ticket.html'
  AND NOT EXISTS (
      SELECT 1 FROM ground_truth_labels corrected
      WHERE corrected.url = 'http://localhost:8080/fixtures/wai-bad/before/tickets.html'
        AND corrected.wcag_criterion = old_label.wcag_criterion
        AND corrected.element_selector IS NOT DISTINCT FROM old_label.element_selector
  );
