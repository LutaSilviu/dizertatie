-- V6: Remove the original ground-truth labels, which were never derived from the W3C BAD markup.
-- In the local fixtures 14 of the 22 selectors match nothing, 4 point at before/ticket.html (the page
-- is before/tickets.html; databases seeded before commit 1876721 hold them under tickets.html), one
-- matches two unrelated selects, and after/home.html -- the accessible negative example -- was stored
-- as an expected violation. DatasetSeeder re-seeds the corrected labels from
-- dataset/ground-truth-labels.json on the next start (see DATASET.md); that includes the two
-- (2.4.1, body) rows deleted here, which survive review.
-- ground_truth_labels has no incoming foreign keys; findings and benchmark runs are untouched.
DELETE FROM ground_truth_labels
WHERE (url, wcag_criterion, element_selector) IN (
    ('http://localhost:8080/fixtures/wai-bad/before/home.html', '1.1.1', 'img[src=''../img/w3c_home_nb.png'']'),
    ('http://localhost:8080/fixtures/wai-bad/before/home.html', '1.1.1', 'img[src=''../img/logo.gif'']'),
    ('http://localhost:8080/fixtures/wai-bad/before/home.html', '1.4.3', 'p.light-text'),
    ('http://localhost:8080/fixtures/wai-bad/before/home.html', '1.4.3', 'a.nav-link'),
    ('http://localhost:8080/fixtures/wai-bad/before/home.html', '1.3.1', 'font[size=''+2'']'),
    ('http://localhost:8080/fixtures/wai-bad/before/home.html', '4.1.2', 'div[onclick]'),
    ('http://localhost:8080/fixtures/wai-bad/before/home.html', '2.4.1', 'body'),
    ('http://localhost:8080/fixtures/wai-bad/after/home.html', '1.1.1', 'img[alt]'),
    ('http://localhost:8080/fixtures/wai-bad/before/survey.html', '2.4.6', 'input[type=''text'']:not([id])'),
    ('http://localhost:8080/fixtures/wai-bad/before/survey.html', '2.4.6', 'select:not([id])'),
    ('http://localhost:8080/fixtures/wai-bad/before/survey.html', '1.3.1', 'form > input[type=''radio'']'),
    ('http://localhost:8080/fixtures/wai-bad/before/survey.html', '4.1.2', 'input[type=''submit'']:not([value])'),
    ('http://localhost:8080/fixtures/wai-bad/before/survey.html', '1.4.3', 'label.hint-text'),
    ('http://localhost:8080/fixtures/wai-bad/before/ticket.html', '2.4.6', 'input[type=''text'']:not([aria-label])'),
    ('http://localhost:8080/fixtures/wai-bad/before/ticket.html', '1.1.1', 'img.decorative:not([alt=''''])'),
    ('http://localhost:8080/fixtures/wai-bad/before/ticket.html', '1.3.1', 'table:not([role=''presentation''])'),
    ('http://localhost:8080/fixtures/wai-bad/before/ticket.html', '2.1.1', 'div.clickable[onclick]'),
    ('http://localhost:8080/fixtures/wai-bad/before/news.html', '1.3.3', 'p.instruction'),
    ('http://localhost:8080/fixtures/wai-bad/before/news.html', '1.4.3', 'span.date'),
    ('http://localhost:8080/fixtures/wai-bad/before/news.html', '4.1.2', 'div.accordion:not([role])'),
    ('http://localhost:8080/fixtures/wai-bad/before/news.html', '2.4.1', 'body'),
    ('http://localhost:8080/fixtures/wai-bad/before/news.html', '1.1.1', 'img.thumbnail:not([alt])'),
    ('http://localhost:8080/fixtures/wai-bad/before/tickets.html', '2.4.6', 'input[type=''text'']:not([aria-label])'),
    ('http://localhost:8080/fixtures/wai-bad/before/tickets.html', '1.1.1', 'img.decorative:not([alt=''''])'),
    ('http://localhost:8080/fixtures/wai-bad/before/tickets.html', '1.3.1', 'table:not([role=''presentation''])'),
    ('http://localhost:8080/fixtures/wai-bad/before/tickets.html', '2.1.1', 'div.clickable[onclick]')
);
