import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch } from '../settings/apiClient'
import styles from './HistoryPage.module.css'

interface ScanRecord {
  id: number | string
  url: string
  method: string
  model: string
  timestamp: string
  severityScore: number
  findingCount: number
}

interface ScansResponse {
  content: ScanRecord[]
  totalPages: number
  number: number
}

function HistoryPage() {
  const [data, setData] = useState<ScansResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    apiFetch(`/api/scans?page=${page}&size=20`)
      .then((res) => {
        if (!res.ok) throw new Error(`Request failed: ${res.status}`)
        return res.json() as Promise<ScansResponse>
      })
      .then((json) => {
        if (!cancelled) {
          setData(json)
          setLoading(false)
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Unknown error')
          setLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [page])

  if (loading) {
    return (
      <div className={styles.page}>
        <h1 className={styles.heading}>Scan History</h1>
        <p className={styles.status}>Loading&hellip;</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.page}>
        <h1 className={styles.heading}>Scan History</h1>
        <p className={styles.errorMsg}>Error: {error}</p>
      </div>
    )
  }

  const scans = data?.content ?? []
  const totalPages = data?.totalPages ?? 1
  const currentPage = data?.number ?? 0

  return (
    <div className={styles.page}>
      <h1 className={styles.heading}>Scan History</h1>
      {scans.length === 0 ? (
        <p className={styles.status}>No scans yet.</p>
      ) : (
        <>
          <div className={styles.tableWrapper}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>URL</th>
                  <th>Method</th>
                  <th>Model</th>
                  <th>Timestamp</th>
                  <th>Severity Score</th>
                  <th>Findings</th>
                </tr>
              </thead>
              <tbody>
                {scans.map((scan) => (
                  <tr key={scan.id}>
                    <td>
                      <Link to={`/report/${scan.id}`}>{scan.id}</Link>
                    </td>
                    <td className={styles.urlCell}>{scan.url}</td>
                    <td>{scan.method}</td>
                    <td>{scan.model}</td>
                    <td>{new Date(scan.timestamp).toLocaleString()}</td>
                    <td>{scan.severityScore}</td>
                    <td>{scan.findingCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className={styles.pagination}>
            <button
              className={styles.pageBtn}
              disabled={currentPage === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </button>
            <span className={styles.pageInfo}>
              Page {currentPage + 1} of {totalPages}
            </span>
            <button
              className={styles.pageBtn}
              disabled={currentPage >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </button>
          </div>
        </>
      )}
    </div>
  )
}

export default HistoryPage
