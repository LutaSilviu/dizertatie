# Raw benchmark results, 16 November 2026

Twelve benchmark runs over the built-in dataset, all three methods, summarised in
[development-history.md](../../development-history.md), section 3.

File names are `<prompt version>_<model>_<run number>_<kind>.json`:

- `metrics`: the response of `GET /benchmark-runs/{id}/metrics` (precision, recall and F1 per
  method and per WCAG principle, with examples of false positives and false negatives);
- `status`: the response of `GET /benchmark-runs/{id}/status` (average time per page and the
  estimated cost).

Timings in the `v2_gpt-6-luna_2`, `v2_gpt-6-luna_3` and `v2_gpt-4o_1` runs were inflated by a
Docker image build running on the same machine; their precision and recall are unaffected.
