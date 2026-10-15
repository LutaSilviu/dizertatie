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

interface MetricsResponse {
  runId: number
  methods: Record<Method, MethodMetrics>
  byCategory: Record<string, Record<Method, MethodMetrics>>
  falsePositiveExamples: unknown[]
  falseNegativeExamples: unknown[]
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

// ---- Main Page ----

function BenchmarkResultPage() {
  const { runId } = useParams<{ runId: string }>()
  const [status, setStatus] = useState<RunStatus | null>(null)
  const [metrics, setMetrics] = useState<MetricsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
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

  const containerStyle: React.CSSProperties = {
    maxWidth: '900px',
    margin: '0 auto',
    padding: '1.5rem',
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  }

  return (
    <div style={containerStyle}>
      <h1 style={{ margin: 0 }}>Benchmark Results</h1>

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
        </>
      )}

      {!error && !status && !metrics && (
        <p>Loading…</p>
      )}
    </div>
  )
}

export default BenchmarkResultPage
