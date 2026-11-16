import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  apiFetch,
  downloadFile,
  formatDateTime,
  networkErrorMessage,
  readApiError,
} from '../settings/apiClient'
import { truncateMiddle } from '../settings/url'
import { METHODS, METHOD_LABELS, formatMethods, runMethods, type Method } from './methods'

// ---- Types (mirror backend DTOs) ----

// BenchmarkRunStatusResponse; the avg* fields are null unless status=COMPLETE.
interface RunStatus {
  id: number
  name: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETE' | 'FAILED'
  pagesDone: number
  totalPages: number
  createdAt: string
  avgLatencyAxeMs: number | null
  avgLatencyAiMs: number | null
  avgLatencyCombinedMs: number | null
  avgCostPerPageUsd: number | null
  /** Methods the run was configured with (newer backends only; absent = all three). */
  methods?: string[] | null
  /** Human-readable failure summary (newer backends only). */
  error?: string | null
}

interface MethodMetrics {
  precision: number
  recall: number
  f1: number
}

// FpFnExample: one flat list across methods, each item tagged with its method.
interface FpFnExample {
  method: Method
  url: string
  wcagCriterion: string | null
  elementSelector: string | null
  description: string | null
}

// BenchmarkMetricsResponse.
interface MetricsResponse {
  runId: number
  methods: Partial<Record<Method, MethodMetrics>>
  byCategory: Record<string, Partial<Record<Method, MethodMetrics>>>
  falsePositiveExamples: FpFnExample[] | null
  falseNegativeExamples: FpFnExample[] | null
  avgCostPerPageUsd: number | null
  avgLatencyMs: Partial<Record<Method, number>> | null
}

// ---- Helpers ----

function fmt(n: number | null | undefined, decimals = 3): string {
  return n == null || Number.isNaN(n) ? '—' : n.toFixed(decimals)
}

const CATEGORIES = ['Perceivable', 'Operable', 'Understandable', 'Robust', 'Unknown']

interface ColMeta {
  label: string
  decimals: number
  /** Which direction counts as "best" for highlighting. */
  better: 'higher' | 'lower'
  value: (method: Method) => number | null
}

function buildCols(metrics: MetricsResponse): ColMeta[] {
  return [
    { label: 'Precision', decimals: 3, better: 'higher', value: (m) => metrics.methods?.[m]?.precision ?? null },
    { label: 'Recall', decimals: 3, better: 'higher', value: (m) => metrics.methods?.[m]?.recall ?? null },
    { label: 'F1', decimals: 3, better: 'higher', value: (m) => metrics.methods?.[m]?.f1 ?? null },
    { label: 'Avg Latency (ms)', decimals: 0, better: 'lower', value: (m) => metrics.avgLatencyMs?.[m] ?? null },
  ]
}

function buildCategoryCols(categoryMethods: Partial<Record<Method, MethodMetrics>>): ColMeta[] {
  return [
    { label: 'Precision', decimals: 3, better: 'higher', value: (m) => categoryMethods[m]?.precision ?? null },
    { label: 'Recall', decimals: 3, better: 'higher', value: (m) => categoryMethods[m]?.recall ?? null },
    { label: 'F1', decimals: 3, better: 'higher', value: (m) => categoryMethods[m]?.f1 ?? null },
  ]
}

/** Best value per column, or null when there is nothing to distinguish (all equal / missing). */
function bestValues(cols: ColMeta[], methods: Method[]): Array<number | null> {
  return cols.map((col) => {
    const vals = methods.map((m) => col.value(m)).filter((v): v is number => v != null)
    if (vals.length < 2 || vals.every((v) => v === vals[0])) return null
    return col.better === 'higher' ? Math.max(...vals) : Math.min(...vals)
  })
}

// ---- Sub-components ----

function MetricsTable({ cols, methods, compact = false }: { cols: ColMeta[]; methods: Method[]; compact?: boolean }) {
  const best = bestValues(cols, methods)
  const pad = compact ? '0.4rem 0.6rem' : '0.5rem 0.75rem'

  const cellStyle = (isHighlight: boolean): React.CSSProperties => ({
    padding: pad,
    textAlign: 'right',
    fontWeight: isHighlight ? 700 : 400,
    color: isHighlight ? '#1a7f64' : 'inherit',
    background: isHighlight ? '#e6f7f2' : 'inherit',
  })

  return (
    <table style={{ borderCollapse: 'collapse', width: '100%', fontSize: compact ? '0.875rem' : '0.9rem' }}>
      <thead>
        <tr style={{ background: '#f5f5f5' }}>
          <th style={{ padding: pad, textAlign: 'left' }}>Method</th>
          {cols.map((c) => (
            <th key={c.label} style={{ padding: pad, textAlign: 'right' }}>
              {c.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {methods.map((method) => (
          <tr key={method} style={{ borderTop: '1px solid #e0e0e0' }}>
            <td style={{ padding: pad, fontWeight: 600 }}>{METHOD_LABELS[method]}</td>
            {cols.map((col, ci) => {
              const val = col.value(method)
              const isHighlight = val != null && val === best[ci]
              return (
                <td key={col.label} style={cellStyle(isHighlight)}>
                  {fmt(val, col.decimals)}
                </td>
              )
            })}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function CategoryBreakdown({ metrics, methods }: { metrics: MetricsResponse; methods: Method[] }) {
  const available = CATEGORIES.filter((cat) => metrics.byCategory?.[cat])
  const [selectedTab, setSelectedTab] = useState<string | null>(null)

  if (available.length === 0) {
    return <p style={{ color: '#888' }}>No per-category data available.</p>
  }

  // Default to the first category that actually has data.
  const activeTab = selectedTab && available.includes(selectedTab) ? selectedTab : available[0]

  const tabStyle = (active: boolean): React.CSSProperties => ({
    padding: '0.5rem 1rem',
    borderWidth: '0 0 2px 0',
    borderStyle: 'solid',
    borderColor: active ? '#1a7f64' : 'transparent',
    background: 'none',
    cursor: 'pointer',
    fontWeight: active ? 600 : 400,
    color: active ? '#1a7f64' : 'inherit',
    fontSize: '0.9rem',
  })

  const categoryData = metrics.byCategory[activeTab]

  return (
    <div>
      <div
        role="tablist"
        style={{ display: 'flex', flexWrap: 'wrap', borderBottom: '1px solid #e0e0e0', marginBottom: '1rem' }}
      >
        {available.map((cat) => (
          <button
            key={cat}
            role="tab"
            aria-selected={activeTab === cat}
            style={tabStyle(activeTab === cat)}
            onClick={() => setSelectedTab(cat)}
          >
            {cat}
          </button>
        ))}
      </div>
      {categoryData && (
        <div style={{ overflowX: 'auto' }}>
          <MetricsTable cols={buildCategoryCols(categoryData)} methods={methods} compact />
        </div>
      )}
    </div>
  )
}

// ---- FP/FN Example Table ----

function ExampleTable({ examples }: { examples: FpFnExample[] }) {
  if (examples.length === 0) {
    return <p style={{ color: '#888', margin: '0.5rem 0' }}>No examples available.</p>
  }
  const thStyle: React.CSSProperties = {
    padding: '0.4rem 0.6rem',
    textAlign: 'left',
    background: '#f5f5f5',
    fontWeight: 600,
    fontSize: '0.8rem',
    whiteSpace: 'nowrap',
  }
  const tdStyle: React.CSSProperties = {
    padding: '0.4rem 0.6rem',
    fontSize: '0.8rem',
    verticalAlign: 'top',
    borderTop: '1px solid #e0e0e0',
    maxWidth: '200px',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  }
  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ borderCollapse: 'collapse', width: '100%' }}>
        <thead>
          <tr>
            <th style={thStyle}>URL</th>
            <th style={thStyle}>WCAG Criterion</th>
            <th style={thStyle}>Element Selector</th>
            <th style={{ ...thStyle, whiteSpace: 'normal', minWidth: '200px' }}>Description</th>
          </tr>
        </thead>
        <tbody>
          {examples.map((ex, i) => (
            <tr key={i}>
              {/* Middle-truncated so the page/file name stays visible; full URL in the tooltip. */}
              <td style={{ ...tdStyle, maxWidth: 'none' }} title={ex.url}>{truncateMiddle(ex.url ?? '', 40)}</td>
              <td style={tdStyle}>{ex.wcagCriterion || '—'}</td>
              <td style={tdStyle} title={ex.elementSelector ?? ''}>{ex.elementSelector || '—'}</td>
              <td style={{ ...tdStyle, maxWidth: '300px', whiteSpace: 'normal' }}>
                {ex.description || '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ---- Method Accordion ----

interface MethodAccordionProps {
  method: Method
  fpExamples: FpFnExample[]
  fnExamples: FpFnExample[]
}

function MethodAccordion({ method, fpExamples, fnExamples }: MethodAccordionProps) {
  const [open, setOpen] = useState(false)
  const [activeTab, setActiveTab] = useState<'fp' | 'fn'>('fp')

  const headerStyle: React.CSSProperties = {
    display: 'flex',
    width: '100%',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '0.6rem 0.75rem',
    background: '#f5f5f5',
    border: '1px solid #e0e0e0',
    borderRadius: open ? '6px 6px 0 0' : '6px',
    cursor: 'pointer',
    userSelect: 'none',
    textAlign: 'left',
    color: 'inherit',
  }

  const tabStyle = (active: boolean): React.CSSProperties => ({
    padding: '0.4rem 0.75rem',
    borderWidth: '0 0 2px 0',
    borderStyle: 'solid',
    borderColor: active ? '#1a7f64' : 'transparent',
    background: 'none',
    cursor: 'pointer',
    fontWeight: active ? 600 : 400,
    color: active ? '#1a7f64' : 'inherit',
    fontSize: '0.85rem',
  })

  return (
    <div style={{ marginBottom: '0.5rem' }}>
      <button type="button" style={headerStyle} onClick={() => setOpen((v) => !v)} aria-expanded={open}>
        <span style={{ fontWeight: 600 }}>{METHOD_LABELS[method]}</span>
        <span style={{ fontSize: '0.85rem', color: '#666' }}>
          {fpExamples.length} FP · {fnExamples.length} FN
          <span style={{ marginLeft: '0.5rem' }}>{open ? '▲' : '▼'}</span>
        </span>
      </button>
      {open && (
        <div style={{ borderWidth: '0 1px 1px', borderStyle: 'solid', borderColor: '#e0e0e0', borderRadius: '0 0 6px 6px', padding: '0.75rem' }}>
          <div style={{ display: 'flex', borderBottom: '1px solid #e0e0e0', marginBottom: '0.75rem' }}>
            <button type="button" style={tabStyle(activeTab === 'fp')} onClick={() => setActiveTab('fp')}>
              False Positives ({fpExamples.length})
            </button>
            <button type="button" style={tabStyle(activeTab === 'fn')} onClick={() => setActiveTab('fn')}>
              False Negatives ({fnExamples.length})
            </button>
          </div>
          <ExampleTable examples={activeTab === 'fp' ? fpExamples : fnExamples} />
        </div>
      )}
    </div>
  )
}

// ---- FP/FN Section ----

function FpFnSection({ metrics, methods }: { metrics: MetricsResponse; methods: Method[] }) {
  const fp = metrics.falsePositiveExamples ?? []
  const fn = metrics.falseNegativeExamples ?? []
  const forMethod = (list: FpFnExample[], method: Method) =>
    list.filter((ex) => (ex.method ?? '').toUpperCase() === method)

  if (fp.length === 0 && fn.length === 0) {
    return <p style={{ color: '#888' }}>No FP/FN examples available.</p>
  }

  return (
    <div>
      <p style={{ margin: '0 0 0.75rem', color: '#718096', fontSize: '0.85rem' }}>
        A sample of mismatches against the ground-truth labels (at most 10 false positives and 10 false
        negatives per method).
      </p>
      {methods.map((method) => (
        <MethodAccordion
          key={method}
          method={method}
          fpExamples={forMethod(fp, method)}
          fnExamples={forMethod(fn, method)}
        />
      ))}
    </div>
  )
}

// ---- Main Page ----

const POLL_INTERVAL_MS = 3000

function BenchmarkResultPage() {
  const { runId } = useParams<{ runId: string }>()
  const [status, setStatus] = useState<RunStatus | null>(null)
  const [metrics, setMetrics] = useState<MetricsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (!runId) return
    let cancelled = false
    let failures = 0
    setStatus(null)
    setMetrics(null)
    setError(null)

    const fetchMetrics = async () => {
      try {
        const res = await apiFetch(`/api/benchmark-runs/${encodeURIComponent(runId)}/metrics`)
        if (!res.ok) {
          throw new Error(`Could not load metrics: ${await readApiError(res)}`)
        }
        const data: MetricsResponse = await res.json()
        if (!cancelled) setMetrics(data)
      } catch (err) {
        if (!cancelled) setError(networkErrorMessage(err))
      }
    }

    // Poll with setTimeout (not setInterval) so requests never overlap.
    const poll = async () => {
      try {
        const res = await apiFetch(`/api/benchmark-runs/${encodeURIComponent(runId)}/status`)
        if (res.status === 404) {
          throw Object.assign(new Error(`Benchmark run #${runId} does not exist.`), { fatal: true })
        }
        if (!res.ok) {
          throw new Error(`Could not load run status: ${await readApiError(res)}`)
        }
        const data: RunStatus = await res.json()
        if (cancelled) return
        failures = 0
        setStatus(data)
        setError(null)

        if (data.status === 'COMPLETE') {
          await fetchMetrics()
          return
        }
        if (data.status === 'FAILED') {
          // The failure banner is rendered from `status` (it includes the backend's `error` summary).
          return
        }
      } catch (err) {
        if (cancelled) return
        failures += 1
        const fatal = (err as { fatal?: boolean }).fatal === true
        setError(networkErrorMessage(err))
        // Tolerate a few transient errors (e.g. backend restart) before giving up.
        if (fatal || failures >= 5) return
      }
      if (!cancelled) timerRef.current = setTimeout(poll, POLL_INTERVAL_MS)
    }

    poll()

    return () => {
      cancelled = true
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
  }, [runId])

  async function handleExportCsv() {
    if (!runId) return
    setExporting(true)
    setExportError(null)
    try {
      await downloadFile(`/api/benchmark-runs/${encodeURIComponent(runId)}/export.csv`, `benchmark-${runId}.csv`)
    } catch (err) {
      setExportError(networkErrorMessage(err))
    } finally {
      setExporting(false)
    }
  }

  // Width/alignment come from the global .app-page container.
  const containerStyle: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  }

  const canExport = status?.status === 'COMPLETE' || status?.status === 'FAILED'
  const active = status && (status.status === 'PENDING' || status.status === 'RUNNING')
  const progressPct =
    status && status.totalPages > 0 ? Math.round((status.pagesDone / status.totalPages) * 100) : 0
  const avgCost = metrics?.avgCostPerPageUsd ?? status?.avgCostPerPageUsd ?? null

  // Methods the run was configured with vs. methods that actually produced metrics
  // (newer backends omit methods that failed entirely, e.g. AI without an API key).
  const requestedMethods = runMethods(status?.methods)
  const shownMethods = metrics
    ? METHODS.filter((m) => metrics.methods?.[m] != null)
    : requestedMethods
  const missingMethods = metrics ? requestedMethods.filter((m) => !shownMethods.includes(m)) : []
  const showsAi = shownMethods.some((m) => m !== 'AXE')
  const runError = status?.error?.trim() || null
  return (
    <div className="app-page" style={containerStyle}>
      <Link to="/benchmark" style={{ color: '#1a7f64', textDecoration: 'none', fontSize: '0.9rem' }}>
        ← All benchmark runs
      </Link>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem' }}>
        <div>
          <h1 style={{ margin: 0 }}>{status?.name || 'Benchmark Results'}</h1>
          {status && (
            <p style={{ margin: '0.25rem 0 0', color: '#718096', fontSize: '0.9rem' }}>
              Run #{status.id} · {status.status} · started {formatDateTime(status.createdAt)} · methods:{' '}
              {formatMethods(status.methods)}
            </p>
          )}
        </div>
        {canExport && (
          <button
            onClick={handleExportCsv}
            disabled={exporting}
            style={{
              padding: '0.5rem 1rem',
              background: '#1a7f64',
              color: '#fff',
              border: 'none',
              borderRadius: '6px',
              cursor: exporting ? 'not-allowed' : 'pointer',
              opacity: exporting ? 0.7 : 1,
              fontSize: '0.9rem',
            }}
          >
            {exporting ? 'Exporting…' : 'Export CSV'}
          </button>
        )}
      </div>

      {exportError && (
        <div role="alert" style={{ color: '#b91c1c', background: '#fef2f2', padding: '0.75rem 1rem', borderRadius: '6px' }}>
          {exportError}
        </div>
      )}

      {error && (
        <div role="alert" style={{ color: '#b91c1c', background: '#fef2f2', padding: '0.75rem 1rem', borderRadius: '6px' }}>
          {error}
        </div>
      )}

      {status?.status === 'FAILED' && (
        <div role="alert" style={{ color: '#b91c1c', background: '#fef2f2', padding: '0.75rem 1rem', borderRadius: '6px' }}>
          <strong>The benchmark run failed.</strong>{' '}
          {runError ?? 'Partial findings may still be available via Export CSV.'}
        </div>
      )}

      {status?.status === 'COMPLETE' && runError && (
        <div
          role="status"
          style={{ color: '#92400e', background: '#fffbeb', border: '1px solid #fcd34d', padding: '0.75rem 1rem', borderRadius: '6px' }}
        >
          <strong>Completed with problems:</strong> {runError}
        </div>
      )}

      {active && (
        <div role="status" aria-live="polite">
          <p style={{ margin: '0 0 0.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <span
              aria-hidden="true"
              style={{
                display: 'inline-block',
                width: '14px',
                height: '14px',
                borderWidth: '2px',
                borderStyle: 'solid',
                borderColor: '#cbd5e0',
                borderTopColor: '#1a7f64',
                borderRadius: '50%',
                animation: 'spin 0.8s linear infinite',
              }}
            />
            {status.status === 'PENDING' ? 'Waiting to start…' : 'Running benchmark…'}{' '}
            {status.totalPages > 0 && `${status.pagesDone} / ${status.totalPages} pages (${progressPct}%)`}
          </p>
          {status.totalPages > 0 && (
            <div style={{ background: '#e0e0e0', borderRadius: '4px', overflow: 'hidden', height: '8px' }}>
              <div
                style={{
                  height: '100%',
                  width: `${progressPct}%`,
                  background: '#1a7f64',
                  transition: 'width 0.4s ease',
                }}
              />
            </div>
          )}
          <p style={{ margin: '0.5rem 0 0', color: '#718096', fontSize: '0.85rem' }}>
            This page updates automatically. Each page is scanned once per method ({formatMethods(status.methods)}).
          </p>
        </div>
      )}

      {status?.status === 'COMPLETE' && !metrics && !error && <p>Loading metrics…</p>}

      {metrics && shownMethods.length === 0 && (
        <p style={{ color: '#888' }}>No method produced results for this run, so there are no metrics to show.</p>
      )}

      {metrics && shownMethods.length > 0 && (
        <>
          <section>
            <h2 style={{ marginTop: 0 }}>Summary</h2>
            {missingMethods.length > 0 && (
              <p style={{ margin: '0 0 0.75rem', color: '#92400e', fontSize: '0.9rem' }}>
                No results for {missingMethods.map((m) => METHOD_LABELS[m]).join(', ')}: every scan with{' '}
                {missingMethods.length === 1 ? 'this method' : 'these methods'} failed, so{' '}
                {missingMethods.length === 1 ? 'it is' : 'they are'} left out of the comparison.
              </p>
            )}
            <div style={{ overflowX: 'auto' }}>
              <MetricsTable cols={buildCols(metrics)} methods={shownMethods} />
            </div>
            {showsAi && (
              <p style={{ margin: '0.5rem 0 0', color: '#4a5568', fontSize: '0.9rem' }}>
                Avg estimated AI cost per page: {avgCost == null ? '—' : `$${avgCost.toFixed(4)}`}
              </p>
            )}
          </section>

          <section>
            <h2 style={{ marginTop: 0 }}>Per-Category Breakdown</h2>
            <CategoryBreakdown metrics={metrics} methods={shownMethods} />
          </section>

          <section>
            <h2 style={{ marginTop: 0 }}>False Positive / False Negative Examples</h2>
            <FpFnSection metrics={metrics} methods={shownMethods} />
          </section>
        </>
      )}

      {!error && !status && !metrics && <p>Loading…</p>}
    </div>
  )
}

export default BenchmarkResultPage
