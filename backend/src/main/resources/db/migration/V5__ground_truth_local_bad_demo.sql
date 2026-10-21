-- V5: Point ground-truth labels at the backend's local copy of the W3C BAD demo.
-- www.w3.org serves headless browsers a Cloudflare challenge page, so scans of the
-- live URLs audited the challenge page instead of the demo.
UPDATE ground_truth_labels
SET url = replace(url, 'https://www.w3.org/WAI/demos/bad/', 'http://localhost:8080/fixtures/wai-bad/')
WHERE url LIKE 'https://www.w3.org/WAI/demos/bad/%';
