import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { apiFetch } from '../settings/apiClient'
import styles from './ReportPage.module.css'

interface ConformanceScores {
  levelA: number
  levelAA: number
  levelAAA: number
}

interface Finding {
  wcagCriterion: string
  wcagLevel: string
  severity: string
  sourceMethod: string
  elementSelector: string
  description: string
}

interface ScanReport {
  id: string
  url: string
  method: string
  model?: string
  timestamp: string
  severityScore: number
  conformanceScores: ConformanceScores
  findings: Finding[]
}

function SeverityBadge({ score }: { score: number }) {
  let colorClass = styles.severityGreen
  let label = 'Low'
  if (score >= 70) {
    colorClass = styles.severityRed
    label = 'High'
  } else if (score >= 30) {
    colorClass = styles.severityAmber
    label = 'Medium'
  }

  return (
    <div className={styles.severityWrapper} aria-label={`Severity score: ${score} out of 100 (${label})`}>
      <div className={styles.severityBar}>
        <div
          className={[styles.severityFill, colorClass].join(' ')}
          style={{ width: `${score}%` }}
        />
      </div>
      <span className={[styles.severityBadge, colorClass].join(' ')}>
        {score} / 100 — {label}
      </span>
    </div>
  )
}

function ConformanceScoresPanel({ scores }: { scores: ConformanceScores }) {
  const items = [
    { label: 'Level A', value: scores.levelA },
    { label: 'Level AA', value: scores.levelAA },
    { label: 'Level AAA', value: scores.levelAAA },
  ]

  return (
    <div className={styles.conformancePanel}>
      {items.map(({ label, value }) => (
        <div key={label} className={styles.conformanceItem}>
          <span className={styles.conformanceLabel}>{label}</span>
          <span className={styles.conformanceValue}>{(value * 100).toFixed(1)}%</span>
          <div className={styles.conformanceBar} aria-hidden="true">
            <div
              className={styles.conformanceFill}
              style={{ width: `${value * 100}%` }}
            />
          </div>
        </div>
      ))}
    </div>
  )
}

function FindingsTable({ findings }: { findings: Finding[] }) {
  if (findings.length === 0) {
    return <p className={styles.emptyFindings}>No findings reported for this scan.</p>
  }

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
          {findings.map((f, i) => (
            <tr key={i}>
              <td>{f.wcagCriterion}</td>
              <td>
                <span className={[styles.levelBadge, styles[`level${f.wcagLevel}`]].join(' ')}>
                  {f.wcagLevel}
                </span>
              </td>
              <td>
                <span className={[styles.severityLabel, styles[`sev${f.severity}`]].join(' ')}>
                  {f.severity}
                </span>
              </td>
              <td>{f.sourceMethod}</td>
              <td>
                <code className={styles.selector}>{f.elementSelector}</code>
              </td>
              <td>{f.description}</td>
            </tr>
          ))}
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

  useEffect(() => {
    if (!scanId) return

    setLoading(true)
    setError(null)

    apiFetch(`/api/scans/${scanId}`)
      .then(async (res) => {
        if (!res.ok) {
          const text = await res.text()
          throw new Error(`Failed to load scan: ${res.status} ${text}`)
        }
        return res.json() as Promise<ScanReport>
      })
      .then((data) => {
        setReport(data)
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Unknown error')
      })
      .finally(() => {
        setLoading(false)
      })
  }, [scanId])

  async function handleExportCsv() {
    if (!scanId) return
    setExporting(true)
    try {
      const res = await apiFetch(`/api/scans/${scanId}/export.csv`)
      if (!res.ok) {
        throw new Error(`Export failed: ${res.status}`)
      }
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `scan-${scanId}.csv`
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

  if (loading) {
    return (
      <div className={styles.statusContainer} role="status" aria-live="polite">
        <div className={styles.spinner} aria-hidden="true" />
        <p>Loading scan report…</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.statusContainer}>
        <p className={styles.errorMsg} role="alert">{error}</p>
        <Link to="/analyzer" className={styles.backLink}>← Back to Analyzer</Link>
      </div>
    )
  }

  if (!report) return null

  const formattedDate = new Date(report.timestamp).toLocaleString()

  return (
    <div className={styles.page}>
      <div className={styles.topBar}>
        <Link to="/analyzer" className={styles.backLink}>← Back to Analyzer</Link>
        <button
          className={styles.exportBtn}
          onClick={handleExportCsv}
          disabled={exporting}
        >
          {exporting ? 'Exporting…' : 'Export CSV'}
        </button>
      </div>

      <h1 className={styles.pageTitle} title={report.url}>
        {report.url}
      </h1>

      <section className={styles.metaSection} aria-label="Scan metadata">
        <dl className={styles.metaList}>
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
            <dd>{formattedDate}</dd>
          </div>
        </dl>
      </section>

      <section className={styles.section} aria-labelledby="severity-heading">
        <h2 id="severity-heading" className={styles.sectionTitle}>Severity Score</h2>
        <SeverityBadge score={report.severityScore} />
      </section>

      <section className={styles.section} aria-labelledby="conformance-heading">
        <h2 id="conformance-heading" className={styles.sectionTitle}>Conformance Scores</h2>
        <ConformanceScoresPanel scores={report.conformanceScores} />
      </section>

      <section className={styles.section} aria-labelledby="findings-heading">
        <h2 id="findings-heading" className={styles.sectionTitle}>
          Findings ({report.findings.length})
        </h2>
        <FindingsTable findings={report.findings} />
      </section>
    </div>
  )
}

export default ReportPage
