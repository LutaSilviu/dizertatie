import { useState, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiFetch } from '../settings/apiClient'
import { useSettings } from '../settings/useSettings'

type ScanMethod = 'AXE' | 'AI' | 'COMBINED'
type GptModel = 'gpt-4o-mini' | 'gpt-4o'

interface ScanResponse {
  scanId: string
}

const METHOD_LABELS: Record<ScanMethod, string> = {
  AXE: 'Axe-Core',
  AI: 'AI',
  COMBINED: 'Combined',
}

const MODELS: { value: GptModel; label: string }[] = [
  { value: 'gpt-4o-mini', label: 'GPT-4o-mini' },
  { value: 'gpt-4o', label: 'GPT-4o' },
]

function isValidUrl(value: string): boolean {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

function AnalyzerForm() {
  const navigate = useNavigate()
  const { model: defaultModel } = useSettings()

  const [url, setUrl] = useState('')
  const [method, setMethod] = useState<ScanMethod>('AXE')
  const [model, setModel] = useState<GptModel>(
    (defaultModel as GptModel) ?? 'gpt-4o-mini'
  )
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [urlError, setUrlError] = useState<string | null>(null)

  const showModel = method === 'AI' || method === 'COMBINED'

  function handleUrlChange(value: string) {
    setUrl(value)
    if (urlError && isValidUrl(value)) {
      setUrlError(null)
    }
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)

    if (!isValidUrl(url)) {
      setUrlError('Please enter a valid http:// or https:// URL.')
      return
    }
    setUrlError(null)

    setLoading(true)
    try {
      const body: { url: string; method: ScanMethod; model?: GptModel } = {
        url,
        method,
      }
      if (showModel) {
        body.model = model
      }

      const data = await apiFetch<ScanResponse>('/api/scans', {
        method: 'POST',
        body: JSON.stringify(body),
      })

      navigate(`/report/${data.scanId}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An unexpected error occurred.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} style={styles.form} noValidate>
      <div style={styles.field}>
        <label htmlFor="analyzer-url" style={styles.label}>
          URL to analyze
        </label>
        <input
          id="analyzer-url"
          type="url"
          value={url}
          onChange={(e) => handleUrlChange(e.target.value)}
          placeholder="https://example.com"
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

      {showModel && (
        <div style={styles.field}>
          <label htmlFor="analyzer-model" style={styles.label}>
            AI model
          </label>
          <select
            id="analyzer-model"
            value={model}
            onChange={(e) => setModel(e.target.value as GptModel)}
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

      <button type="submit" disabled={loading} style={styles.button}>
        {loading ? (
          <>
            <span style={styles.spinner} aria-hidden="true" />
            Analyzing…
          </>
        ) : (
          'Analyze'
        )}
      </button>
    </form>
  )
}

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
  input: {
    padding: '0.5rem 0.75rem',
    fontSize: '1rem',
    border: '1px solid #ccc',
    borderRadius: '4px',
    outline: 'none',
  },
  inputError: {
    borderColor: '#d32f2f',
  },
  select: {
    padding: '0.5rem 0.75rem',
    fontSize: '1rem',
    border: '1px solid #ccc',
    borderRadius: '4px',
  },
  fieldset: {
    border: '1px solid #ccc',
    borderRadius: '4px',
    padding: '0.75rem 1rem',
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
    border: '2px solid rgba(255,255,255,0.4)',
    borderTopColor: '#fff',
    borderRadius: '50%',
    animation: 'spin 0.7s linear infinite',
  },
}

export default AnalyzerForm
