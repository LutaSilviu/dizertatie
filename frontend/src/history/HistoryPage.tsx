import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { apiFetch, formatDateTime, networkErrorMessage, readApiError } from '../settings/apiClient'
import { RATING_COLORS, RATING_LABELS, rateScore } from '../report/severity'
import { truncateMiddle } from '../settings/url'
import styles from './HistoryPage.module.css'

const PAGE_SIZE = 20

// Mirrors backend ScanSummaryResponse.
interface ScanRecord {
  id: number
  url: string
  method: string
  model: string | null
  timestamp: string | null
  severityScore: number | null
  findingCount: number
}

// Mirrors backend ScanHistoryResponse (not Spring's Page: no totalPages/number fields).
interface ScansResponse {
  content: ScanRecord[]
  page: number
  size: number
  totalElements: number
}

function HistoryPage() {
  const [data, setData] = useState<ScansResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  // The page lives in the URL (?page=2, 1-based) so Back from a report returns to the same page.
  const [searchParams, setSearchParams] = useSearchParams()
  const rawPage = searchParams.get('page')
  const page = rawPage !== null && /^\d+$/.test(rawPage) && Number(rawPage) > 1 ? Number(rawPage) - 1 : 0
  const setPage = (zeroBased: number) => {
    setSearchParams(zeroBased > 0 ? { page: String(zeroBased + 1) } : {})
  }

  // Normalise invalid values (?page=-3, ?page=abc, ?page=1, ?page=007) in place, without a history entry.
  useEffect(() => {
    if (rawPage === null) return
    const canonical = page > 0 ? String(page + 1) : null
    if (rawPage !== canonical) {
      const next = new URLSearchParams(searchParams)
      if (canonical) next.set('page', canonical)
      else next.delete('page')
      setSearchParams(next, { replace: true })
    }
  }, [rawPage, page, searchParams, setSearchParams])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    apiFetch(`/api/scans?page=${page}&size=${PAGE_SIZE}`)
      .then(async (res) => {
        if (!res.ok) throw new Error(await readApiError(res))
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
          setError(networkErrorMessage(err))
          setLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [page])

  if (loading && !data) {
    return (
      <div className={`app-page ${styles.page}`}>
        <h1 className={styles.heading}>Scan History</h1>
        <p className={styles.status}>Loading&hellip;</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className={`app-page ${styles.page}`}>
        <h1 className={styles.heading}>Scan History</h1>
        <p className={styles.errorMsg} role="alert">Error: {error}</p>
      </div>
    )
  }

  const scans = data?.content ?? []
  const size = data?.size || PAGE_SIZE
  const totalElements = data?.totalElements ?? 0
  const totalPages = Math.max(1, Math.ceil(totalElements / size))
  const currentPage = data?.page ?? page

  return (
    <div className={`app-page ${styles.page}`}>
      <h1 className={styles.heading}>Scan History</h1>
      {scans.length === 0 && totalElements > 0 ? (
        <p className={styles.status}>
          Page {page + 1} is past the end of the history.{' '}
          <Link to="/history">Go to the first page</Link>.
        </p>
      ) : scans.length === 0 ? (
        <p className={styles.status}>
          No scans yet. <Link to="/analyzer">Analyze a website</Link> to get started.
        </p>
      ) : (
        <>
          <div className={styles.tableWrapper} aria-busy={loading}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>URL</th>
                  <th>Method</th>
                  <th>Model</th>
                  <th>Timestamp</th>
                  <th title="Accessibility score from 0 to 100; higher is better">Score</th>
                  <th>Findings</th>
                </tr>
              </thead>
              <tbody>
                {scans.map((scan) => {
                  const score = scan.severityScore
                  const rating = score == null ? null : rateScore(score)
                  return (
                    <tr key={scan.id}>
                      <td>
                        <Link to={`/report/${scan.id}`}>#{scan.id}</Link>
                      </td>
                      <td className={styles.urlCell} title={scan.url}>
                        <Link to={`/report/${scan.id}`} aria-label={scan.url}>
                          {truncateMiddle(scan.url)}
                        </Link>
                      </td>
                      <td>{scan.method}</td>
                      <td>{scan.model || '—'}</td>
                      <td className={styles.nowrap}>{formatDateTime(scan.timestamp)}</td>
                      <td className={styles.scoreCell}>
                        {score == null || rating == null ? (
                          '—'
                        ) : (
                          <span style={{ color: RATING_COLORS[rating] }}>
                            {score.toFixed(1)}
                            <span className={styles.scoreLabel}>{RATING_LABELS[rating]}</span>
                          </span>
                        )}
                      </td>
                      <td>{scan.findingCount}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div className={styles.pagination}>
            <button
              className={styles.pageBtn}
              disabled={loading || currentPage === 0}
              onClick={() => setPage(Math.max(0, currentPage - 1))}
            >
              Previous
            </button>
            <span className={styles.pageInfo}>
              Page {currentPage + 1} of {totalPages} ({totalElements} scans)
            </span>
            <button
              className={styles.pageBtn}
              disabled={loading || currentPage >= totalPages - 1}
              onClick={() => setPage(currentPage + 1)}
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
