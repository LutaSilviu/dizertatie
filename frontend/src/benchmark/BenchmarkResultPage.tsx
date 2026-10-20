import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { apiFetch } from '../settings/apiClient'

// ---- Types ----

interface RunStatus {
  id: number
  name: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETE' | 'FAILED'
  pagesDone: number
  totalPages: number
  createdAt: string
}

interface MethodMetrics {
  precision: number
  recall: number
  f1: number
}

type Method = 'AXE' | 'AI' | 'COMBINED'

interface FpFnExample {
  url: string
  criterion: string
  selector: string
  description: string
}

interface MetricsResponse {
  runId: number
  methods: Record<Method, MethodMetrics>
  byCategory: Record<string, Record<Method, MethodMetrics>>
  falsePositiveExamples: Record<Method, FpFnExample[]>
  falseNegativeExamples: Record<Method, FpFnExample[]>
  avgCostPerPageUsd: number
  avgLatencyMs: Record<Method, number>
}

// ---- Helpers ----

function fmt(n: number, decimals = 3): string {
  return n.toFixed(decimals)
}

const METHODS: Method[] = ['AXE', 'AI', 'COMBINED']
const CATEGORIES = ['Perceivable', 'Operable', 'Understandable', 'Robust']

interface ColMeta {
  label: string
  value: (method: Method, metrics: MetricsResponse) => number
}

// Build column definitions (needs full metrics for latency/cost columns)
function buildCols(metrics: MetricsResponse): ColMeta[] {
  return [
    {
      label: 'Precision',
      value: (m) => metrics.methods[m]?.precision ?? 0,
    },
    {
      label: 'Recall',
      value: (m) => metrics.methods[m]?.recall ?? 0,
    },
    {
      label: 'F1',
      value: (m) => metrics.methods[m]?.f1 ?? 0,
    },
    {
      label: 'Avg Latency (ms)',
      value: (m) => metrics.avgLatencyMs?.[m] ?? 0,
    },
    {
      label: 'Avg Cost (USD)',
      value: (_m) => metrics.avgCostPerPageUsd ?? 0,
    },
  ]
}

function buildCategoryCols(categoryMethods: Record<Method, MethodMetrics>): Array<{
  label: string
  value: (m: Method) => number
}> {
  return [
    { label: 'Precision', value: (m) => categoryMethods[m]?.precision ?? 0 },
    { label: 'Recall', value: (m) => categoryMethods[m]?.recall ?? 0 },
    { label: 'F1', value: (m) => categoryMethods[m]?.f1 ?? 0 },
  ]
}

// ---- Sub-components ----

interface SummaryTableProps {
  metrics: MetricsResponse
}

function SummaryTable({ metrics }: SummaryTableProps) {
  const cols = buildCols(metrics)

  // Find best (highest) value per column
  const bestPerCol = cols.map((col) => {
    const vals = METHODS.map((m) => col.value(m, metrics))
    return Math.max(...vals)
  })

  const cellStyle = (isHighlight: boolean): React.CSSProperties => ({
    padding: '0.5rem 0.75rem',
    textAlign: 'right',
    fontWeight: isHighlight ? 700 : 400,
    color: isHighlight ? '#1a7f64' : 'inherit',
    background: isHighlight ? '#e6f7f2' : 'inherit',
  })

  return (
    <table style={{ borderCollapse: 'collapse', width: '100%', fontSize: '0.9rem' }}>
      <thead>
        <tr style={{ background: '#f5f5f5' }}>
          <th style={{ padding: '0.5rem 0.75rem', textAlign: 'left' }}>Method</th>
          {cols.map((c) => (
            <th key={c.label} style={{ padding: '0.5rem 0.75rem', textAlign: 'right' }}>
              {c.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {METHODS.map((method) => (
          <tr key={method} style={{ borderTop: '1px solid #e0e0e0' }}>
            <td style={{ padding: '0.5rem 0.75rem', fontWeight: 600 }}>{method}</td>
            {cols.map((col, ci) => {
              const val = col.value(method, metrics)
              const isHighlight = val === bestPerCol[ci]
              return (
                <td key={col.label} style={cellStyle(isHighlight)}>
                  {fmt(val, col.label.includes('Latency') ? 0 : col.label.includes('Cost') ? 4 : 3)}
                </td>
              )
            })}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

interface CategoryTableProps {
  categoryMethods: Record<Method, MethodMetrics>
}

function CategoryTable({ categoryMethods }: CategoryTableProps) {
  const cols = buildCategoryCols(categoryMethods)

  const bestPerCol = cols.map((col) => {
    const vals = METHODS.map((m) => col.value(m))
    return Math.max(...vals)
  })

  const cellStyle = (isHighlight: boolean): React.CSSProperties => ({
    padding: '0.4rem 0.6rem',
    textAlign: 'right',
    fontWeight: isHighlight ? 700 : 400,
    color: isHighlight ? '#1a7f64' : 'inherit',
    background: isHighlight ? '#e6f7f2' : 'inherit',
  })

  return (
    <table style={{ borderCollapse: 'collapse', width: '100%', fontSize: '0.875rem' }}>
      <thead>
        <tr style={{ background: '#f5f5f5' }}>
          <th style={{ padding: '0.4rem 0.6rem', textAlign: 'left' }}>Method</th>
          {cols.map((c) => (
            <th key={c.label} style={{ padding: '0.4rem 0.6rem', textAlign: 'right' }}>
              {c.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {METHODS.map((method) => (
          <tr key={method} style={{ borderTop: '1px solid #e0e0e0' }}>
            <td style={{ padding: '0.4rem 0.6rem', fontWeight: 600 }}>{method}</td>
            {cols.map((col, ci) => {
              const val = col.value(method)
              const isHighlight = val === bestPerCol[ci]
              return (
                <td key={col.label} style={cellStyle(isHighlight)}>
                  {fmt(val)}
                </td>
              )
            })}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

interface CategoryBreakdownProps {
  metrics: MetricsResponse
}

function CategoryBreakdown({ metrics }: CategoryBreakdownProps) {
  const [activeTab, setActiveTab] = useState<string>(CATEGORIES[0])

  const available = CATEGORIES.filter((cat) => metrics.byCategory?.[cat])

  if (available.length === 0) {
    return <p style={{ color: '#888' }}>No per-category data available.</p>
  }

  const tabStyle = (active: boolean): React.CSSProperties => ({
    padding: '0.5rem 1rem',
    border: 'none',
    borderBottom: active ? '2px solid #1a7f64' : '2px solid transparent',
    background: 'none',
    cursor: 'pointer',
    fontWeight: active ? 600 : 400,
    color: active ? '#1a7f64' : 'inherit',
    fontSize: '0.9rem',
  })

  const categoryData = metrics.byCategory[activeTab]

  return (
    <div>
      <div style={{ display: 'flex', borderBottom: '1px solid #e0e0e0', marginBottom: '1rem' }}>
        {available.map((cat) => (
          <button key={cat} style={tabStyle(activeTab === cat)} onClick={() => setActiveTab(cat)}>
            {cat}
          </button>
        ))}
      </div>
      {categoryData && <CategoryTable categoryMethods={categoryData as Record<Method, MethodMetrics>} />}
    </div>
  )
}

// ---- FP/FN Example Table ----

interface ExampleTableProps {
  examples: FpFnExample[]
}

function ExampleTable({ examples }: ExampleTableProps) {
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
          {examples.slice(0, 10).map((ex, i) => (
            <tr key={i}>
              <td style={tdStyle} title={ex.url}>{ex.url}</td>
              <td style={tdStyle}>{ex.criterion}</td>
              <td style={tdStyle} title={ex.selector}>{ex.selector}</td>
              <td style={{ ...tdStyle, maxWidth: '300px', whiteSpace: 'normal' }}>{ex.description}</td>
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
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '0.6rem 0.75rem',
    background: '#f5f5f5',
    border: '1px solid #e0e0e0',
    borderRadius: open ? '6px 6px 0 0' : '6px',
    cursor: 'pointer',
    userSelect: 'none',
  }

  const tabStyle = (active: boolean): React.CSSProperties => ({
    padding: '0.4rem 0.75rem',
    border: 'none',
    borderBottom: active ? '2px solid #1a7f64' : '2px solid transparent',
    background: 'none',
    cursor: 'pointer',
    fontWeight: active ? 600 : 400,
    color: active ? '#1a7f64' : 'inherit',
    fontSize: '0.85rem',
  })

  return (
    <div style={{ marginBottom: '0.5rem' }}>
      <div style={headerStyle} onClick={() => setOpen((v) => !v)} role="button" aria-expanded={open}>
        <span style={{ fontWeight: 600 }}>{method}</span>
        <span style={{ fontSize: '0.85rem', color: '#666' }}>
          {fpExamples.length} FP · {fnExamples.length} FN
          <span style={{ marginLeft: '0.5rem' }}>{open ? '▲' : '▼'}</span>
        </span>
      </div>
      {open && (
        <div style={{ border: '1px solid #e0e0e0', borderTop: 'none', borderRadius: '0 0 6px 6px', padding: '0.75rem' }}>
          <div style={{ display: 'flex', borderBottom: '1px solid #e0e0e0', marginBottom: '0.75rem' }}>
            <button style={tabStyle(activeTab === 'fp')} onClick={() => setActiveTab('fp')}>
              False Positives ({Math.min(fpExamples.length, 10)} shown)
            </button>
            <button style={tabStyle(activeTab === 'fn')} onClick={() => setActiveTab('fn')}>
              False Negatives ({Math.min(fnExamples.length, 10)} shown)
            </button>
          </div>
          <ExampleTable examples={activeTab === 'fp' ? fpExamples : fnExamples} />
        </div>
      )}
    </div>
  )
}

// ---- FP/FN Section ----

interface FpFnSectionProps {
  metrics: MetricsResponse
}

function FpFnSection({ metrics }: FpFnSectionProps) {
  const fp = metrics.falsePositiveExamples
  const fn = metrics.falseNegativeExamples

  // Support both flat array (legacy) and per-method map
  const getExamples = (source: Record<Method, FpFnExample[]> | FpFnExample[] | undefined, method: Method): FpFnExample[] => {
    if (!source) return []
    if (Array.isArray(source)) return source as FpFnExample[]
    return (source as Record<Method, FpFnExample[]>)[method] ?? []
  }

  const hasAny = METHODS.some(
    (m) => getExamples(fp as Record<Method, FpFnExample[]>, m).length > 0 || getExamples(fn as Record<Method, FpFnExample[]>, m).length > 0
  )

  if (!hasAny) {
    return <p style={{ color: '#888' }}>No FP/FN examples available.</p>
  }

  return (
    <div>
      {METHODS.map((method) => (
        <MethodAccordion
          key={method}
          method={method}
          fpExamples={getExamples(fp as Record<Method, FpFnExample[]>, method)}
          fnExamples={getExamples(fn as Record<Method, FpFnExample[]>, method)}
        />
      ))}
    </div>
  )
}

// ---- Main Page ----

function BenchmarkResultPage() {
  const { runId } = useParams<{ runId: string }>()
  const [status, setStatus] = useState<RunStatus | null>(null)
  const [metrics, setMetrics] = useState<MetricsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const stopPolling = () => {
    if (intervalRef.current !== null) {
      clearInterval(intervalRef.current)
      intervalRef.current = null
    }
  }

  const fetchMetrics = async () => {
    if (!runId) return
    try {
      const res = await apiFetch(`/api/benchmark-runs/${runId}/metrics`)
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `Metrics fetch failed (${res.status})`)
      }
      const data: MetricsResponse = await res.json()
      setMetrics(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch metrics.')
    }
  }

  const fetchStatus = async () => {
    if (!runId) return
    try {
      const res = await apiFetch(`/api/benchmark-runs/${runId}/status`)
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `Status fetch failed (${res.status})`)
      }
      const data: RunStatus = await res.json()
      setStatus(data)

      if (data.status === 'COMPLETE') {
        stopPolling()
        await fetchMetrics()
      } else if (data.status === 'FAILED') {
        stopPolling()
        setError('The benchmark run failed.')
      }
    } catch (err) {
      stopPolling()
      setError(err instanceof Error ? err.message : 'Failed to fetch run status.')
    }
  }

  useEffect(() => {
    fetchStatus()
    intervalRef.current = setInterval(fetchStatus, 3000)
    return () => stopPolling()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [runId])

  async function handleExportCsv() {
    if (!runId) return
    setExporting(true)
    try {
      const res = await apiFetch(`/api/benchmark-runs/${runId}/export.csv`)
      if (!res.ok) {
        throw new Error(`Export failed: ${res.status}`)
      }
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `benchmark-${runId}.csv`
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Export failed')
    } finally {
      setExporting(false)
    }
  }

  const containerStyle: React.CSSProperties = {
    maxWidth: '900px',
    margin: '0 auto',
    padding: '1.5rem',
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  }

  const canExport = status?.status === 'COMPLETE' || status?.status === 'FAILED'

  return (
    <div style={containerStyle}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <h1 style={{ margin: 0 }}>Benchmark Results</h1>
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

      {error && (
        <div role="alert" style={{ color: '#b91c1c', background: '#fef2f2', padding: '0.75rem 1rem', borderRadius: '6px' }}>
          {error}
        </div>
      )}

      {!error && status && (status.status === 'PENDING' || status.status === 'RUNNING') && (
        <div>
          <p style={{ margin: '0 0 0.5rem' }}>
            {status.status === 'PENDING' ? 'Waiting to start…' : 'Running benchmark…'}{' '}
            {status.totalPages > 0 && `(${status.pagesDone} / ${status.totalPages} pages)`}
          </p>
          {status.totalPages > 0 && (
            <div style={{ background: '#e0e0e0', borderRadius: '4px', overflow: 'hidden', height: '8px' }}>
              <div
                style={{
                  height: '100%',
                  width: `${Math.round((status.pagesDone / status.totalPages) * 100)}%`,
                  background: '#1a7f64',
                  transition: 'width 0.4s ease',
                }}
              />
            </div>
          )}
        </div>
      )}

      {metrics && (
        <>
          <section>
            <h2 style={{ marginTop: 0 }}>Summary</h2>
            <div style={{ overflowX: 'auto' }}>
              <SummaryTable metrics={metrics} />
            </div>
          </section>

          <section>
            <h2 style={{ marginTop: 0 }}>Per-Category Breakdown</h2>
            <CategoryBreakdown metrics={metrics} />
          </section>

          <section>
            <h2 style={{ marginTop: 0 }}>False Positive / False Negative Examples</h2>
            <FpFnSection metrics={metrics} />
          </section>
        </>
      )}

      {!error && !status && !metrics && (
        <p>Loading…</p>
      )}
    </div>
  )
}

export default BenchmarkResultPage
