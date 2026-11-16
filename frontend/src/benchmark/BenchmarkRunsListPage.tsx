import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch, formatDateTime, networkErrorMessage, readApiError } from '../settings/apiClient'
import { formatMethods } from './methods'

interface BenchmarkRun {
  id: number
  name: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETE' | 'FAILED'
  pagesDone: number
  totalPages: number
  createdAt: string
  /** Newer backends only. */
  methods?: string[] | null
  /** Human-readable failure summary (newer backends only). */
  error?: string | null
}

function statusBadgeStyle(status: BenchmarkRun['status']): React.CSSProperties {
  const base: React.CSSProperties = {
    display: 'inline-block',
    padding: '0.2rem 0.5rem',
    borderRadius: '4px',
    fontSize: '0.75rem',
    fontWeight: 600,
    textTransform: 'uppercase',
  }
  switch (status) {
    case 'COMPLETE':
      return { ...base, background: '#e6f7f2', color: '#1a7f64' }
    case 'RUNNING':
      return { ...base, background: '#fff3cd', color: '#856404' }
    case 'PENDING':
      return { ...base, background: '#e9ecef', color: '#555' }
    case 'FAILED':
      return { ...base, background: '#fef2f2', color: '#b91c1c' }
    default:
      return base
  }
}

function BenchmarkRunsListPage() {
  const [runs, setRuns] = useState<BenchmarkRun[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const hasActiveRuns = runs.some((r) => r.status === 'PENDING' || r.status === 'RUNNING')

  useEffect(() => {
    let cancelled = false
    const load = () =>
      apiFetch('/api/benchmark-runs')
        .then(async (res) => {
          if (!res.ok) {
            throw new Error(`Failed to load benchmark runs: ${await readApiError(res)}`)
          }
          return res.json() as Promise<BenchmarkRun[]>
        })
        .then((data) => {
          if (!cancelled) {
            setRuns(data)
            setError(null)
          }
        })
        .catch((err) => {
          if (!cancelled) setError(networkErrorMessage(err))
        })
        .finally(() => {
          if (!cancelled) setLoading(false)
        })

    load()
    // Keep progress of in-flight runs up to date.
    const timer = hasActiveRuns ? setInterval(load, 5000) : null
    return () => {
      cancelled = true
      if (timer) clearInterval(timer)
    }
  }, [hasActiveRuns])

  // Width/alignment come from the global .app-page container.
  const containerStyle: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  }

  const thStyle: React.CSSProperties = {
    padding: '0.5rem 0.75rem',
    textAlign: 'left',
    background: '#f5f5f5',
    fontWeight: 600,
    fontSize: '0.875rem',
    whiteSpace: 'nowrap',
  }

  const tdStyle: React.CSSProperties = {
    padding: '0.5rem 0.75rem',
    fontSize: '0.875rem',
    borderTop: '1px solid #e0e0e0',
    verticalAlign: 'middle',
  }

  return (
    <div className="app-page" style={containerStyle}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <h1 style={{ margin: 0 }}>Benchmark Runs</h1>
        <Link
          to="/benchmark/new"
          style={{
            padding: '0.5rem 1rem',
            background: '#1a7f64',
            color: '#fff',
            borderRadius: '6px',
            textDecoration: 'none',
            fontSize: '0.9rem',
            fontWeight: 600,
          }}
        >
          New Benchmark
        </Link>
      </div>

      {error && (
        <div role="alert" style={{ color: '#b91c1c', background: '#fef2f2', padding: '0.75rem 1rem', borderRadius: '6px' }}>
          {error}
        </div>
      )}

      {loading && !error && <p>Loading…</p>}

      {!loading && !error && runs.length === 0 && (
        <p style={{ color: '#888' }}>No runs yet. Start one with the "New Benchmark" button.</p>
      )}

      {!loading && !error && runs.length > 0 && (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ borderCollapse: 'collapse', width: '100%' }}>
            <thead>
              <tr>
                <th style={thStyle}>Name</th>
                <th style={thStyle}>Status</th>
                <th style={thStyle}>Methods</th>
                <th style={thStyle}>Pages Done / Total</th>
                <th style={thStyle}>Created At</th>
              </tr>
            </thead>
            <tbody>
              {runs.map((run) => (
                <tr key={run.id} style={{ background: 'inherit' }}>
                  <td style={tdStyle}>
                    <Link
                      to={`/benchmark/${run.id}`}
                      style={{ color: '#1a7f64', textDecoration: 'none', fontWeight: 500 }}
                    >
                      {run.name || `Run #${run.id}`}
                    </Link>
                  </td>
                  <td style={tdStyle}>
                    <span style={statusBadgeStyle(run.status)}>{run.status}</span>
                    {run.error && (
                      <span
                        title={run.error}
                        style={{
                          display: 'block',
                          marginTop: '0.25rem',
                          maxWidth: '260px',
                          fontSize: '0.75rem',
                          color: run.status === 'FAILED' ? '#b91c1c' : '#92400e',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {run.status === 'FAILED' ? '' : 'Warning: '}
                        {run.error}
                      </span>
                    )}
                  </td>
                  <td style={tdStyle}>{formatMethods(run.methods)}</td>
                  <td style={tdStyle}>
                    {run.totalPages > 0 ? `${run.pagesDone} / ${run.totalPages}` : '—'}
                  </td>
                  <td style={tdStyle}>{formatDateTime(run.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default BenchmarkRunsListPage
