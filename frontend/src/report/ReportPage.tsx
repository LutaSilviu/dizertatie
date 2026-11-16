import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { apiFetch, downloadFile, formatDateTime, networkErrorMessage, readApiError } from '../settings/apiClient'
import { normaliseSeverity, severityRank } from './severity'
import styles from './ReportPage.module.css'

// Mirrors backend FindingResponse; AI findings have no rule id and may have null fields.
interface Finding {
  wcagCriterion: string | null
  wcagLevel: string | null
  ruleId: string | null
  severity: string | null
  sourceMethod: string | null
  elementSelector: string | null
  description: string | null
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

// Mirrors backend ScanResponse.
interface ScanReport {
  id: number
  url: string
  method: string
  model?: string | null
  axeVersion?: string | null
  browserVersion?: string | null
  timestamp: string | null
  status: 'COMPLETED' | 'TIMED_OUT' | 'RATE_LIMITED' | 'BLOCKED' | 'FAILED'
  failureReason: string | null
  affectedElementsByImpact: Record<string, number> | null
  findingsBySource: Record<string, number> | null
  distinctRuleCount: number | null
  wcagCriteriaWithFindings: string[] | null
  findings: Finding[] | null
  manualReviewItems: ManualReviewItem[] | null
}

const SOURCE_LABELS: Record<string, string> = {
  AXE: 'axe-core only',
  AI: 'AI only',
  COMBINED: 'axe-core and AI',
}

function impactOrder([a]: [string, number], [b]: [string, number]): number {
  return severityRank(a) - severityRank(b)
}

function FindingsTable({ findings }: { findings: Finding[] }) {
  if (findings.length === 0) {
    return <p className={styles.emptyFindings}>No automated findings were reported for this completed scan.</p>
  }

  const sorted = [...findings].sort((a, b) => severityRank(a.severity) - severityRank(b.severity))

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
                <td>{f.ruleId || '—'}</td>
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

  const completed = report.status === 'COMPLETED'
  const findings = report.findings ?? []
  const criteria = report.wcagCriteriaWithFindings ?? []
  const impacts = Object.entries(report.affectedElementsByImpact ?? {}).sort(impactOrder)
  const sources = Object.entries(report.findingsBySource ?? {})
  const manualReviewItems = report.manualReviewItems ?? []
  const method = (report.method ?? '').toUpperCase()

  return (
    <div className="app-page">
      <div className={styles.topBar}>
        <div className={styles.topLinks}>
          <Link to="/analyzer" className={styles.backLink}>← New scan</Link>
          <Link to="/history" className={styles.backLink}>Scan history</Link>
        </div>
        {completed && (
          <button
            className={styles.exportBtn}
            onClick={handleExportCsv}
            disabled={exporting}
          >
            {exporting ? 'Exporting…' : 'Export CSV'}
          </button>
        )}
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
          {report.axeVersion && (
            <div className={styles.metaItem}>
              <dt>axe-core</dt>
              <dd>{report.axeVersion}</dd>
            </div>
          )}
          {report.browserVersion && (
            <div className={styles.metaItem}>
              <dt>Browser</dt>
              <dd>{report.browserVersion}</dd>
            </div>
          )}
          <div className={styles.metaItem}>
            <dt>Scanned</dt>
            <dd>{formatDateTime(report.timestamp)}</dd>
          </div>
        </dl>
      </section>

      {!completed ? (
        <section className={styles.failurePanel} aria-labelledby="scan-failure-heading" role="status">
          <h2 id="scan-failure-heading">Scan {report.status.replace(/_/g, ' ').toLowerCase()}</h2>
          <p>{report.failureReason || 'The scan could not be completed.'}</p>
          <p>No accessibility findings were collected for this attempt.</p>
        </section>
      ) : (
        <>
      <section className={styles.section} aria-labelledby="evidence-heading">
        <h2 id="evidence-heading" className={styles.sectionTitle}>Scan evidence</h2>
        <div className={styles.evidenceGrid}>
          <div className={styles.evidenceCard}>
            <h3>Reported elements by impact</h3>
            {impacts.length === 0 ? (
              <p>0 reported elements</p>
            ) : (
              <ul>
                {impacts.map(([impact, count]) => (
                  <li key={impact}><span>{impact}</span><strong>{count}</strong></li>
                ))}
              </ul>
            )}
            <p className={styles.reviewNote}>Counts every finding listed below.</p>
          </div>
          {sources.length > 0 && (
            <div className={styles.evidenceCard}>
              <h3>Reported elements by source</h3>
              <ul>
                {sources.map(([source, count]) => (
                  <li key={source}><span>{SOURCE_LABELS[source] ?? source}</span><strong>{count}</strong></li>
                ))}
              </ul>
            </div>
          )}
          {method !== 'AI' && (
            <div className={styles.evidenceCard}>
              <h3>Distinct axe-core rules with findings</h3>
              <p className={styles.evidenceCount}>{report.distinctRuleCount ?? 0}</p>
              {method === 'COMBINED' && (
                <p className={styles.reviewNote}>AI findings are not tied to a rule.</p>
              )}
            </div>
          )}
          <div className={styles.evidenceCard}>
            <h3>WCAG criteria with findings</h3>
            {criteria.length === 0 ? (
              <p>No WCAG criteria with findings.</p>
            ) : (
              <ul className={styles.criteriaList}>
                {criteria.map((criterion) => <li key={criterion}>{criterion}</li>)}
              </ul>
            )}
            <p className={styles.reviewNote}>Criteria without findings are not confirmed as passed.</p>
          </div>
        </div>
      </section>

      <section className={styles.section} aria-labelledby="findings-heading">
        <h2 id="findings-heading" className={styles.sectionTitle}>
          Reported elements ({findings.length})
        </h2>
        <FindingsTable findings={findings} />
      </section>

      <ManualReviewSection items={manualReviewItems} />
        </>
      )}
    </div>
  )
}

export default ReportPage
