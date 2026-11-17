// Diagnostic harness: renders the actual TSX components on the server, without a browser.
// Exit 1 means one or more desired behavior checks failed. No product code is modified.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createRequire } = require('node:module');
const repo = path.resolve(__dirname, '../../..');
const frontendRequire = createRequire(path.join(repo, 'frontend/package.json'));
const ts = frontendRequire('typescript');
const React = frontendRequire('react');
const { renderToStaticMarkup } = frontendRequire('react-dom/server');

function loadComponents(relativePath, names) {
  const filename = path.join(repo, relativePath);
  const source = fs.readFileSync(filename, 'utf8') + '\nexport { ' + names.join(', ') + ' };';
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const module = { exports: {} };
  vm.runInNewContext(output, {
    exports: module.exports, module,
    require: (name) => name.endsWith('.module.css')
      ? { default: new Proxy({}, { get: (_, key) => key }) }
      : name.startsWith('.') ? {} : frontendRequire(name),
  }, { filename });
  return module.exports;
}
const report = loadComponents('frontend/src/report/ReportPage.tsx', ['ConformanceScoresPanel', 'SeverityBadge']);
const benchmark = loadComponents('frontend/src/benchmark/BenchmarkResultPage.tsx', ['SummaryTable', 'MethodAccordion']);
let failures = 0;
function check(name, passed, evidence) {
  console.log((passed ? 'PASS ' : 'FAIL ') + name + '\n  ' + evidence);
  if (!passed) failures++;
}
const conformanceHtml = renderToStaticMarkup(React.createElement(report.ConformanceScoresPanel,
  { scores: { levelA: 100, levelAA: 80, levelAAA: 0 } }));
const percentages = conformanceHtml.match(/\d+(?:\.\d+)?%/g) || [];
check('API percentages stay in the range 0..100', percentages.every(v => parseFloat(v) <= 100), percentages.join(', '));
const severityHtml = renderToStaticMarkup(React.createElement(report.SeverityBadge, { score: 100 }));
check('Clean score 100 is shown as low severity', severityHtml.includes('(Low)'), severityHtml);
const metrics = {
  methods: { AXE: { precision: 1, recall: 1, f1: 1 }, AI: { precision: 0.5, recall: 0.5, f1: 0.5 }, COMBINED: { precision: 0.9, recall: 0.9, f1: 0.9 } },
  avgLatencyMs: { AXE: 100, AI: 1000, COMBINED: 2000 }, avgCostPerPageUsd: 0.01,
};
const summaryHtml = renderToStaticMarkup(React.createElement(benchmark.SummaryTable, { metrics }));
const rows = summaryHtml.match(/<tr[^>]*>.*?<\/tr>/g) || [];
const highlightedLatency = rows.slice(1).filter(row => /background:#e6f7f2[^>]*>2000<\/td>/.test(row));
check('Lowest latency receives the best-value highlight', highlightedLatency.length === 0,
  'COMBINED latency 2000 receives green best-value styling: ' + (highlightedLatency.length > 0));
const accordionHtml = renderToStaticMarkup(React.createElement(benchmark.MethodAccordion,
  { method: 'AXE', fpExamples: [], fnExamples: [] }));
check('Accordion trigger is natively keyboard accessible', /^<div[^>]*>\s*<button/.test(accordionHtml), accordionHtml);
console.log(`\n${failures} failed desired-behavior checks (diagnostic evidence, not a browser test).`);
process.exitCode = failures ? 1 : 0;
