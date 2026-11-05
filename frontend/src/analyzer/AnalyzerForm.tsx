import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiFetch, networkErrorMessage, readApiErrorDetail } from '../settings/apiClient'
import { isAiUnavailable, useAiStatus, withAxeNote } from '../settings/aiStatus'
import { normalizeUrl } from '../settings/url'
import { readAiModelSavedAt, useSettings, type AiModel } from '../settings/useSettings'

type ScanMethod = 'AXE' | 'AI' | 'COMBINED'

interface ScanResponse {
  id: number
}

const METHOD_LABELS: Record<ScanMethod, string> = {
  AXE: 'Axe-Core',
  AI: 'AI',
  COMBINED: 'Combined',
}

const MODELS: { value: AiModel; label: string }[] = [
  { value: 'gpt-6-luna', label: 'GPT-6 Luna' },
  { value: 'gpt-4o-mini', label: 'GPT-4o-mini' },
  { value: 'gpt-4o', label: 'GPT-4o' },
]

// Local copies of the W3C "Before and After Demonstration" served by the backend under
// /fixtures (see backend/src/main/resources/static/fixtures/wai-bad). The URL is fetched by
// the backend itself, so localhost:8080 is correct both on the host and inside Docker.
const FIXTURE_BASE = 'http://localhost:8080/fixtures/wai-bad'
const SAMPLE_PAGES: { label: string; url: string }[] = [
  { label: 'W3C BAD demo – Home (inaccessible)', url: `${FIXTURE_BASE}/before/home.html` },
  { label: 'W3C BAD demo – News (inaccessible)', url: `${FIXTURE_BASE}/before/news.html` },
  { label: 'W3C BAD demo – Tickets (inaccessible)', url: `${FIXTURE_BASE}/before/tickets.html` },
  { label: 'W3C BAD demo – Survey (inaccessible)', url: `${FIXTURE_BASE}/before/survey.html` },
  { label: 'W3C BAD demo – Home (fixed)', url: `${FIXTURE_BASE}/after/home.html` },
]

// Restores the form when the user comes Back from a report.
const FORM_STORAGE_KEY = 'analyzerForm'

interface StoredForm {
  url?: string
  method?: ScanMethod
  /** Only set when the user picked a model here, overriding the Settings default. */
  model?: AiModel
  /** When the user picked `model` (ms epoch); compared with the Settings save time. */
  modelSetAt?: number
}

function readStoredForm(): StoredForm {
  try {
    const raw = sessionStorage.getItem(FORM_STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw) as StoredForm
    const model = MODELS.some((m) => m.value === parsed.model) ? parsed.model : undefined
    const modelSetAt = typeof parsed.modelSetAt === 'number' ? parsed.modelSetAt : undefined
    // A model saved in Settings after the Analyzer override wins.
    const overrideValid = !!model && !!modelSetAt && modelSetAt > readAiModelSavedAt()
    return {
      url: typeof parsed.url === 'string' ? parsed.url : undefined,
      method: parsed.method && parsed.method in METHOD_LABELS ? parsed.method : undefined,
      model: overrideValid ? model : undefined,
      modelSetAt: overrideValid ? modelSetAt : undefined,
    }
  } catch {
    return {}
  }
}

function writeStoredForm(form: StoredForm) {
  try {
    sessionStorage.setItem(FORM_STORAGE_KEY, JSON.stringify(form))
  } catch {
    // Storage unavailable: the form just will not be restored.
  }
}

const BOT_BLOCK_PATTERN =
  // Includes "Target site responded with HTTP 403" / 429 (rate limiting).
  /\bbot\b|challenge|captcha|blocked|cloudflare|forbidden|access denied|too many requests|\b(403|429)\b/i

/**
 * Maps a failed scan response to a message that tells the user what to do next.
 * A specific backend explanation (`{"error": ...}`) is always shown as-is, for every status;
 * generic texts are only used when the body is empty or a proxy (nginx/Vite) error page.
 */
async function scanErrorMessage(res: Response, method: ScanMethod): Promise<string> {
  const { message, fromBackend } = await readApiErrorDetail(res)

  if (fromBackend) {
    const botHint = BOT_BLOCK_PATTERN.test(message)
      ? ' The site may be blocking automated browsers; try another URL or a sample page.'
      : ''
    switch (res.status) {
      case 400:
        return `The scan request was rejected: ${message}`
      case 401:
        return message
      case 502:
      case 504:
        return `The site could not be audited: ${message}${botHint}`
      case 503:
        return method === 'AXE' ? message : withAxeNote(message, 'Axe-Core still works.')
      default:
        return `The scan failed: ${message}${botHint}`
    }
  }

  switch (res.status) {
    case 400:
      return 'The scan request was rejected. Check the URL and the selected method/AI model.'
    case 504:
      return method === 'AXE'
        ? 'The scan took too long and the proxy stopped waiting for the server. Try again, or try another page.'
        : 'The scan took too long and the proxy stopped waiting for the server. Try again, or use the faster Axe-Core method.'
    case 500:
      return 'The scan failed on the server. The page may not have loaded in time, or the AI service returned an error. Check the backend logs and try again.'
    default:
      return message
  }
}

function progressHint(method: ScanMethod, seconds: number): string {
  if (seconds < 5) return 'Opening the page in a headless browser…'
  if (method === 'AXE') return 'Running Axe-Core rules on the page…'
  if (seconds < 15) return 'Capturing the page and running the checks…'
  return 'Waiting for the AI model to analyze the page. This can take a minute or more…'
}

function AnalyzerForm() {
  const navigate = useNavigate()
  const { aiModel: defaultModel } = useSettings()
  const aiStatus = useAiStatus()

  const [stored] = useState(readStoredForm)
  const [url, setUrl] = useState(stored.url ?? '')
  const [method, setMethod] = useState<ScanMethod>(stored.method ?? 'AXE')
  const [model, setModelState] = useState<AiModel>(stored.model ?? defaultModel)
  const [modelSetAt, setModelSetAt] = useState<number | undefined>(stored.modelSetAt)
  const setModel = (m: AiModel) => {
    setModelState(m)
    setModelSetAt(Date.now())
  }
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [urlError, setUrlError] = useState<string | null>(null)
  const [elapsed, setElapsed] = useState(0)
  const abortRef = useRef<AbortController | null>(null)
  const mountedRef = useRef(true)

  useEffect(() => {
    // The model is stored only as an explicit override, so a later Settings save takes effect.
    writeStoredForm(modelSetAt ? { url, method, model, modelSetAt } : { url, method })
  }, [url, method, model, modelSetAt])

  useEffect(() => {
    if (!loading) return
    setElapsed(0)
    const started = Date.now()
    const timer = setInterval(() => setElapsed(Math.floor((Date.now() - started) / 1000)), 1000)
    return () => clearInterval(timer)
  }, [loading])

  // The request is deliberately NOT aborted on unmount: the server finishes the scan regardless
  // and it shows up in History. We only stop touching state / navigating once unmounted.
  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  const showModel = method === 'AI' || method === 'COMBINED'
  const aiWarning = showModel && isAiUnavailable(aiStatus) ? aiStatus.message : null

  function handleUrlChange(value: string) {
    setUrl(value)
    if (urlError && normalizeUrl(value)) {
      setUrlError(null)
    }
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)

    const normalized = normalizeUrl(url)
    if (!normalized) {
      setUrlError('Please enter a valid web address, e.g. example.com or https://example.com.')
      return
    }
    setUrlError(null)
    if (normalized !== url) setUrl(normalized)

    const controller = new AbortController()
    abortRef.current = controller
    setLoading(true)
    try {
      const body: { url: string; method: ScanMethod; model?: AiModel } = {
        url: normalized,
        method,
      }
      if (showModel) {
        body.model = model
      }

      const response = await apiFetch('/api/scans', {
        method: 'POST',
        body: JSON.stringify(body),
        signal: controller.signal,
      })

      if (!response.ok) {
        throw new Error(await scanErrorMessage(response, method))
      }

      const data = await response.json() as ScanResponse
      if (mountedRef.current) navigate(`/report/${data.id}`)
    } catch (err) {
      if (controller.signal.aborted || !mountedRef.current) return
      setError(networkErrorMessage(err))
    } finally {
      if (abortRef.current === controller) abortRef.current = null
      if (!controller.signal.aborted && mountedRef.current) setLoading(false)
    }
  }

  function handleCancel() {
    abortRef.current?.abort()
    abortRef.current = null
    setLoading(false)
    setError('Stopped waiting. The server may still finish the scan; it will then appear in History.')
  }

  return (
    <form onSubmit={handleSubmit} style={styles.form} noValidate>
      <div style={styles.field}>
        <label htmlFor="analyzer-url" style={styles.label}>
          URL to analyze
        </label>
        <input
          id="analyzer-url"
          type="text"
          inputMode="url"
          autoComplete="url"
          value={url}
          onChange={(e) => handleUrlChange(e.target.value)}
          placeholder="example.com or https://example.com"
          required
          aria-describedby={urlError ? 'analyzer-url-error' : undefined}
          aria-invalid={urlError ? 'true' : undefined}
          style={{
            ...styles.input,
            ...(urlError ? styles.inputError : {}),
          }}
          disabled={loading}
        />
        {urlError && (
          <span id="analyzer-url-error" role="alert" style={styles.errorText}>
            {urlError}
          </span>
        )}
        <div style={styles.sampleRow}>
          <label htmlFor="analyzer-sample" style={styles.sampleLabel}>
            Try a sample page:
          </label>
          <select
            id="analyzer-sample"
            value=""
            onChange={(e) => {
              if (e.target.value) handleUrlChange(e.target.value)
            }}
            disabled={loading}
            style={styles.sampleSelect}
          >
            <option value="">Choose a local test page…</option>
            {SAMPLE_PAGES.map((p) => (
              <option key={p.url} value={p.url}>
                {p.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <fieldset style={styles.fieldset}>
        <legend style={styles.legend}>Analysis method</legend>
        <div style={styles.radioGroup}>
          {(Object.keys(METHOD_LABELS) as ScanMethod[]).map((m) => (
            <label key={m} style={styles.radioLabel}>
              <input
                type="radio"
                name="method"
                value={m}
                checked={method === m}
                onChange={() => setMethod(m)}
                disabled={loading}
                style={styles.radioInput}
              />
              {METHOD_LABELS[m]}
            </label>
          ))}
        </div>
      </fieldset>

      {aiWarning && (
        <div role="status" style={styles.warningBanner}>
          <strong>AI analysis is unavailable:</strong> {withAxeNote(aiWarning, 'Axe-Core still works.')}
        </div>
      )}

      {showModel && (
        <div style={styles.field}>
          <label htmlFor="analyzer-model" style={styles.label}>
            AI model
          </label>
          <select
            id="analyzer-model"
            value={model}
            onChange={(e) => setModel(e.target.value as AiModel)}
            disabled={loading}
            style={styles.select}
          >
            {MODELS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
      )}

      {error && (
        <div role="alert" style={styles.errorBanner}>
          {error}
        </div>
      )}

      <div style={styles.actions}>
        <button
          type="submit"
          disabled={loading}
          style={{ ...styles.button, ...(loading ? styles.buttonBusy : {}) }}
        >
          {loading ? (
            <>
              <span style={styles.spinner} aria-hidden="true" />
              Analyzing… {elapsed}s
            </>
          ) : (
            'Analyze'
          )}
        </button>
        {loading && (
          <button type="button" onClick={handleCancel} style={styles.cancelButton}>
            Stop waiting
          </button>
        )}
      </div>

      {loading && (
        <div role="status" aria-live="polite" style={styles.progress}>
          <p style={{ margin: 0 }}>
            {progressHint(method, elapsed)}{' '}
            {method === 'AXE'
              ? 'Axe-Core scans usually take 10–30 seconds.'
              : 'AI and combined scans usually take 30–120 seconds.'}{' '}
            You will be taken to the report when it is ready.
          </p>
          <p style={{ margin: '0.25rem 0 0' }}>
            You can leave this page — the scan keeps running on the server and will appear in History.
          </p>
        </div>
      )}
    </form>
  )
}

const BORDER = '#ccc'

const styles: Record<string, React.CSSProperties> = {
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.25rem',
    maxWidth: '560px',
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.375rem',
  },
  label: {
    fontWeight: 600,
    fontSize: '0.9rem',
  },
  legend: {
    fontWeight: 600,
    fontSize: '0.9rem',
    marginBottom: '0.5rem',
    padding: 0,
  },
  // Longhand border properties so inputError can override the colour without
  // React's "mixing shorthand and non-shorthand" warning.
  input: {
    padding: '0.5rem 0.75rem',
    fontSize: '1rem',
    borderWidth: '1px',
    borderStyle: 'solid',
    borderColor: BORDER,
    borderRadius: '4px',
  },
  inputError: {
    borderColor: '#d32f2f',
  },
  select: {
    padding: '0.5rem 0.75rem',
    fontSize: '1rem',
    borderWidth: '1px',
    borderStyle: 'solid',
    borderColor: BORDER,
    borderRadius: '4px',
  },
  sampleRow: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    flexWrap: 'wrap',
    fontSize: '0.85rem',
  },
  sampleLabel: {
    color: '#4a5568',
  },
  sampleSelect: {
    padding: '0.25rem 0.5rem',
    fontSize: '0.85rem',
    borderWidth: '1px',
    borderStyle: 'solid',
    borderColor: BORDER,
    borderRadius: '4px',
    maxWidth: '100%',
  },
  fieldset: {
    borderWidth: '1px',
    borderStyle: 'solid',
    borderColor: BORDER,
    borderRadius: '4px',
    padding: '0.75rem 1rem',
    margin: 0,
  },
  radioGroup: {
    display: 'flex',
    gap: '1.5rem',
    flexWrap: 'wrap',
  },
  radioLabel: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.375rem',
    cursor: 'pointer',
    fontSize: '0.95rem',
  },
  radioInput: {
    cursor: 'pointer',
  },
  actions: {
    display: 'flex',
    gap: '0.75rem',
    alignItems: 'center',
    flexWrap: 'wrap',
  },
  buttonBusy: {
    cursor: 'progress',
    opacity: 0.85,
  },
  cancelButton: {
    padding: '0.6rem 1.25rem',
    fontSize: '1rem',
    background: '#fff',
    color: '#1976d2',
    border: '1px solid #1976d2',
    borderRadius: '4px',
    cursor: 'pointer',
  },
  progress: {
    margin: 0,
    color: '#4a5568',
    fontSize: '0.9rem',
  },
  button: {
    alignSelf: 'flex-start',
    padding: '0.6rem 1.5rem',
    fontSize: '1rem',
    fontWeight: 600,
    background: '#1976d2',
    color: '#fff',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    opacity: 1,
  },
  errorText: {
    color: '#d32f2f',
    fontSize: '0.85rem',
  },
  warningBanner: {
    background: '#fffbeb',
    color: '#92400e',
    border: '1px solid #fcd34d',
    borderRadius: '4px',
    padding: '0.75rem 1rem',
    fontSize: '0.9rem',
  },
  errorBanner: {
    background: '#fdecea',
    color: '#d32f2f',
    border: '1px solid #f5c6cb',
    borderRadius: '4px',
    padding: '0.75rem 1rem',
    fontSize: '0.9rem',
  },
  spinner: {
    display: 'inline-block',
    width: '14px',
    height: '14px',
    borderWidth: '2px',
    borderStyle: 'solid',
    borderColor: 'rgba(255,255,255,0.4)',
    borderTopColor: '#fff',
    borderRadius: '50%',
    animation: 'spin 0.7s linear infinite',
  },
}

export default AnalyzerForm
