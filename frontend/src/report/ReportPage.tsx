import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { apiFetch, downloadFile, formatDateTime, networkErrorMessage, readApiError } from '../settings/apiClient'
import { RATING_LABELS, normaliseSeverity, rateScore, severityRank } from './severity'
import styles from './ReportPage.module.css'

// Mirrors backend ConformanceScores: pass rates as percentages (0–100).
interface ConformanceScores {
  levelA: number
  levelAA: number
  levelAAA: number
}

// Mirrors backend FindingResponse; AI findings may have null fields.
interface Finding {
  wcagCriterion: string | null
  wcagLevel: string | null
  severity: string | null
  sourceMethod: string | null
  elementSelector: string | null
  description: string | null
}

// Mirrors backend ScanResponse.
interface ScanReport {
  id: number
  url: string
  method: string
  /** Not currently returned by the backend ScanResponse; shown when present. */
  model?: string | null
  timestamp: string | null
  severityScore: number
  conformanceScores: ConformanceScores | null
  findings: Finding[] | null
}

const RATING_CLASS = {
  good: styles.severityGreen,
  fair: styles.severityAmber,
  poor: styles.severityRed,
}

function SeverityBadge({ score }: { score: number }) {
  const rating = rateScore(score)
  const colorClass = RATING_CLASS[rating]
  const label = RATING_LABELS[rating]
  const display = Math.round(score * 10) / 10

  return (
    <div>
      <div
        className={styles.severityWrapper}
        aria-label={`Accessibility score: ${display} out of 100 (${label})`}
      >
        <div className={styles.severityBar}>
          <div
            className={[styles.severityFill, colorClass].join(' ')}
            style={{ width: `${Math.max(0, Math.min(100, score))}%` }}
          />
        </div>
        <span className={[styles.severityBadge, colorClass].join(' ')}>
          {display} / 100 — {label}
        </span>
      </div>
      <p className={styles.scoreHint}>Higher is better: 100 means no issues were found.</p>
    </div>
  )
}

function ConformanceScoresPanel({ scores, method }: { scores: ConformanceScores; method: string }) {
  // Axe-Core only runs A/AA rules, so an AAA pass rate for an Axe-only scan would be meaningless.
  const aaaTested = method.toUpperCase() !== 'AXE'
  const items = [
    { label: 'Level A', value: scores.levelA, tested: true },
    { label: 'Level AA', value: scores.levelAA, tested: true },
    { label: 'Level AAA', value: scores.levelAAA, tested: aaaTested },
  ]

  return (
    <div className={styles.conformancePanel}>
      {items.map(({ label, value, tested }) => {
        if (!tested) {
          return (
            <div key={label} className={[styles.conformanceItem, styles.notTested].join(' ')}>
              <span className={styles.conformanceLabel}>{label}</span>
              <span className={styles.conformanceValue}>—</span>
              <span className={styles.notTestedText}>Not tested (Axe-Core checks only A and AA rules)</span>
            </div>
          )
        }
        const pct = Math.max(0, Math.min(100, value ?? 0))
        return (
          <div key={label} className={styles.conformanceItem}>
            <span className={styles.conformanceLabel}>{label}</span>
            <span className={styles.conformanceValue}>{pct.toFixed(1)}%</span>
            <div className={styles.conformanceBar} aria-hidden="true">
              <div className={styles.conformanceFill} style={{ width: `${pct}%` }} />
            </div>
          </div>
        )
      })}
      <p className={styles.scoreHint}>
        Share of WCAG success criteria at each level with no reported violation.
      </p>
    </div>
  )
}

function FindingsTable({ findings }: { findings: Finding[] }) {
  if (findings.length === 0) {
    return <p className={styles.emptyFindings}>No findings reported for this scan.</p>
  }

  const sorted = [...findings].sort((a, b) => severityRank(a.severity) - severityRank(b.severity))

  return (
    <div className={styles.tableWrapper}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">WCAG Criterion</th>
            <th scope="col">Level</th>
            <th scope="col">Severity</th>
            <th scope="col">Source</th>
            <th scope="col">Element Selector</th>
            <th scope="col">Description</th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((f, i) => {
            const level = (f.wcagLevel ?? '').trim().toUpperCase()
            const sev = normaliseSeverity(f.severity)
            return (
              <tr key={i}>
                <td>{f.wcagCriterion || '—'}</td>
                <td>
                  {level ? (
                    <span className={[styles.levelBadge, styles[`level${level}`] ?? ''].join(' ').trim()}>
                      {level}
                    </span>
                  ) : (
                    '—'
                  )}
                </td>
                <td>
                  <span className={[styles.severityLabel, styles[`sev${sev}`] ?? ''].join(' ').trim()}>
                    {f.severity || 'unknown'}
                  </span>
                </td>
                <td>{f.sourceMethod || '—'}</td>
                <td>
                  {f.elementSelector ? (
                    <code className={styles.selector}>{f.elementSelector}</code>
                  ) : (
                    '—'
                  )}
                </td>
                <td>{f.description || '—'}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function ReportPage() {
  const { scanId } = useParams<{ scanId: string }>()
  const [report, setReport] = useState<ScanReport | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)

  useEffect(() => {
    if (!scanId) return

    let cancelled = false
    setLoading(true)
    setError(null)

    apiFetch(`/api/scans/${encodeURIComponent(scanId)}`)
      .then(async (res) => {
        if (!res.ok) {
          const reason =
            res.status === 404 ? `Scan #${scanId} does not exist.` : await readApiError(res)
          throw new Error(`Failed to load scan: ${reason}`)
        }
        return res.json() as Promise<ScanReport>
      })
      .then((data) => {
        if (!cancelled) setReport(data)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(networkErrorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [scanId])

  async function handleExportCsv() {
    if (!scanId) return
    setExporting(true)
    setExportError(null)
    try {
      await downloadFile(`/api/scans/${encodeURIComponent(scanId)}/export.csv`, `scan-${scanId}.csv`)
    } catch (err) {
      setExportError(networkErrorMessage(err))
    } finally {
      setExporting(false)
    }
  }

  if (loading) {
    return (
      <div className={`app-page ${styles.statusContainer}`} role="status" aria-live="polite">
        <div className={styles.spinner} aria-hidden="true" />
        <p>Loading scan report…</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className={`app-page ${styles.statusContainer}`}>
        <p className={styles.errorMsg} role="alert">{error}</p>
        <div className={styles.topLinks}>
          <Link to="/analyzer" className={styles.backLink}>← Back to Analyzer</Link>
          <Link to="/history" className={styles.backLink}>Scan history</Link>
        </div>
      </div>
    )
  }

  if (!report) return null

  const findings = report.findings ?? []

  return (
    <div className="app-page">
      <div className={styles.topBar}>
        <div className={styles.topLinks}>
          <Link to="/analyzer" className={styles.backLink}>← New scan</Link>
          <Link to="/history" className={styles.backLink}>Scan history</Link>
        </div>
        <button
          className={styles.exportBtn}
          onClick={handleExportCsv}
          disabled={exporting}
        >
          {exporting ? 'Exporting…' : 'Export CSV'}
        </button>
      </div>
      {exportError && (
        <p className={styles.errorMsg} role="alert" style={{ textAlign: 'left', marginBottom: '1rem' }}>
          {exportError}
        </p>
      )}

      <h1 className={styles.pageTitle} title={report.url}>
        {report.url}
      </h1>

      <section className={styles.metaSection} aria-label="Scan metadata">
        <dl className={styles.metaList}>
          <div className={styles.metaItem}>
            <dt>Scan</dt>
            <dd>#{report.id}</dd>
          </div>
          <div className={styles.metaItem}>
            <dt>Method</dt>
            <dd>{report.method}</dd>
          </div>
          {report.model && (
            <div className={styles.metaItem}>
              <dt>Model</dt>
              <dd>{report.model}</dd>
            </div>
          )}
          <div className={styles.metaItem}>
            <dt>Scanned</dt>
            <dd>{formatDateTime(report.timestamp)}</dd>
          </div>
        </dl>
      </section>

      <section className={styles.section} aria-labelledby="severity-heading">
        <h2 id="severity-heading" className={styles.sectionTitle}>Accessibility Score</h2>
        <SeverityBadge score={report.severityScore ?? 0} />
      </section>

      <section className={styles.section} aria-labelledby="conformance-heading">
        <h2 id="conformance-heading" className={styles.sectionTitle}>Conformance Scores</h2>
        {report.conformanceScores ? (
          <ConformanceScoresPanel scores={report.conformanceScores} method={report.method ?? ''} />
        ) : (
          <p className={styles.emptyFindings}>No conformance scores available.</p>
        )}
      </section>

      <section className={styles.section} aria-labelledby="findings-heading">
        <h2 id="findings-heading" className={styles.sectionTitle}>
          Findings ({findings.length})
        </h2>
        <FindingsTable findings={findings} />
      </section>
    </div>
  )
}

export default ReportPage
