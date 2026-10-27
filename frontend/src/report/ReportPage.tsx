import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { apiFetch } from '../settings/apiClient'
import styles from './ReportPage.module.css'

interface Finding {
  wcagCriterion: string
  wcagLevel: string
  ruleId: string
  severity: string
  sourceMethod: string
  elementSelector: string
  description: string
}

interface ManualReviewElement {
  target: string
  html: string
  failureSummary: string
}

interface ManualReviewItem {
  ruleId: string
  description: string
  impact: string
  help: string
  helpUrl: string
  elements: ManualReviewElement[]
}

interface ScanReport {
  id: string
  url: string
  method: string
  model?: string
  timestamp: string
  affectedElementsByImpact: Record<string, number>
  distinctRuleCount: number
  wcagCriteriaWithFindings: string[]
  findings: Finding[]
  manualReviewItems: ManualReviewItem[]
}

function FindingsTable({ findings }: { findings: Finding[] }) {
  if (findings.length === 0) {
    return <p className={styles.emptyFindings}>No automated findings were reported for this completed scan.</p>
  }

  return (
    <div className={styles.tableWrapper}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">WCAG Criterion</th>
            <th scope="col">Level</th>
            <th scope="col">Rule</th>
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
              <td>{f.ruleId || '—'}</td>
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

function ManualReviewSection({ items }: { items: ManualReviewItem[] }) {
  if (items.length === 0) return null

  return (
    <section className={styles.section} aria-labelledby="manual-review-heading">
      <h2 id="manual-review-heading" className={styles.sectionTitle}>
        Needs manual review ({items.length})
      </h2>
      <p className={styles.reviewNote}>These checks need a person to decide whether the page has an accessibility issue. They are not counted as violations.</p>
      <ul className={styles.reviewList}>
        {items.map((item) => (
          <li key={item.ruleId} className={styles.reviewItem}>
            <h3>{item.help || item.ruleId}</h3>
            <p>{item.description}</p>
            <p>Impact: {item.impact || 'not specified'} · Rule: <code>{item.ruleId}</code></p>
            {item.helpUrl && <a href={item.helpUrl} target="_blank" rel="noreferrer">axe-core guidance</a>}
            {item.elements.length > 0 && (
              <ul className={styles.reviewElements}>
                {item.elements.map((element, index) => (
                  <li key={`${element.target}-${index}`}>
                    {element.target && <code>{element.target}</code>}
                    {element.failureSummary && <p>{element.failureSummary}</p>}
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
    </section>
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

      <section className={styles.section} aria-labelledby="evidence-heading">
        <h2 id="evidence-heading" className={styles.sectionTitle}>Scan evidence</h2>
        <div className={styles.evidenceGrid}>
          <div className={styles.evidenceCard}>
            <h3>Affected elements by axe impact</h3>
            {Object.keys(report.affectedElementsByImpact).length === 0 ? (
              <p>0 affected elements</p>
            ) : (
              <ul>
                {Object.entries(report.affectedElementsByImpact).map(([impact, count]) => (
                  <li key={impact}><span>{impact}</span><strong>{count}</strong></li>
                ))}
              </ul>
            )}
          </div>
          <div className={styles.evidenceCard}>
            <h3>Distinct rules with findings</h3>
            <p className={styles.evidenceCount}>{report.distinctRuleCount}</p>
          </div>
          <div className={styles.evidenceCard}>
            <h3>WCAG criteria with findings</h3>
            {report.wcagCriteriaWithFindings.length === 0 ? (
              <p>No WCAG criteria with findings.</p>
            ) : (
              <ul className={styles.criteriaList}>
                {report.wcagCriteriaWithFindings.map((criterion) => <li key={criterion}>{criterion}</li>)}
              </ul>
            )}
            <p className={styles.reviewNote}>Criteria without findings are not confirmed as passed.</p>
          </div>
        </div>
      </section>

      <section className={styles.section} aria-labelledby="findings-heading">
        <h2 id="findings-heading" className={styles.sectionTitle}>
          Affected elements ({report.findings.length})
        </h2>
        <FindingsTable findings={report.findings} />
      </section>

      <ManualReviewSection items={report.manualReviewItems} />
    </div>
  )
}

export default ReportPage
