import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiFetch, networkErrorMessage, readApiError } from '../settings/apiClient'
import { isAiUnavailable, useAiStatus, withAxeNote } from '../settings/aiStatus'
import { normalizeUrl } from '../settings/url'
import { useSettings, type AiModel } from '../settings/useSettings'

// POST /benchmark-runs returns 202 with {"runId": <id>}.
interface BenchmarkRunResponse {
  runId: number
}

type Method = 'AXE' | 'AI' | 'COMBINED'

const METHOD_OPTIONS: { value: Method; label: string }[] = [
  { value: 'AXE', label: 'Axe-Core' },
  { value: 'AI', label: 'AI' },
  { value: 'COMBINED', label: 'Combined (Axe-Core + AI)' },
]

const ALL_METHODS: Method[] = METHOD_OPTIONS.map((o) => o.value)

function BenchmarkForm() {
  const navigate = useNavigate()
  const { aiModel } = useSettings()
  const aiStatus = useAiStatus()
  const aiDown = isAiUnavailable(aiStatus)

  const [name, setName] = useState('')
  const [useBuiltinSet, setUseBuiltinSet] = useState(true)
  const [urlsText, setUrlsText] = useState('')
  const [model, setModel] = useState<AiModel>(aiModel)
  const [methods, setMethods] = useState<Method[]>(ALL_METHODS)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const userTouchedMethods = useRef(false)

  // When AI is known to be unavailable, pre-uncheck the AI-based methods (unless the user already chose).
  useEffect(() => {
    if (aiDown && !userTouchedMethods.current) {
      setMethods((prev) => prev.filter((m) => m === 'AXE'))
    }
  }, [aiDown])

  const toggleMethod = (m: Method, checked: boolean) => {
    userTouchedMethods.current = true
    setMethods((prev) => {
      const next = checked ? [...prev, m] : prev.filter((x) => x !== m)
      return ALL_METHODS.filter((v) => next.includes(v)) // keep canonical order
    })
  }

  const usesAi = methods.some((m) => m !== 'AXE')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (methods.length === 0) {
      setError('Select at least one method.')
      return
    }

    const urls: string[] = []
    if (!useBuiltinSet) {
      const lines = urlsText
        .split(/\r?\n/)
        .map((u) => u.trim())
        .filter(Boolean)
      if (lines.length === 0) {
        setError('Enter at least one URL, or use the built-in URL set.')
        return
      }
      const invalid: string[] = []
      for (const line of lines) {
        const normalized = normalizeUrl(line)
        if (normalized) urls.push(normalized)
        else invalid.push(line)
      }
      if (invalid.length > 0) {
        setError(`These lines are not valid web addresses: ${invalid.join(', ')}`)
        return
      }
      // Show the normalised URLs (e.g. with https:// added) in the textarea.
      setUrlsText(urls.join('\n'))
    }

    setLoading(true)
    try {
      const response = await apiFetch('/api/benchmark-runs', {
        method: 'POST',
        body: JSON.stringify({ name: name.trim() || undefined, useBuiltinSet, urls, model, methods }),
      })

      if (!response.ok) {
        throw new Error(`Could not start the benchmark: ${await readApiError(response)}`)
      }

      const data: BenchmarkRunResponse = await response.json()
      navigate(`/benchmark/${data.runId}`)
    } catch (err) {
      setError(networkErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', maxWidth: '560px' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
        <label htmlFor="run-name">Run name (optional)</label>
        <input
          id="run-name"
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Benchmark run"
          style={{ padding: '0.4rem 0.6rem' }}
        />
      </div>

      <div>
        <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={useBuiltinSet}
            onChange={(e) => setUseBuiltinSet(e.target.checked)}
          />
          Use built-in URL set (pages with ground-truth labels)
        </label>
      </div>

      {!useBuiltinSet && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
          <label htmlFor="urls-input">URLs (one per line)</label>
          <textarea
            id="urls-input"
            value={urlsText}
            onChange={(e) => setUrlsText(e.target.value)}
            rows={6}
            placeholder={'example.com\nhttps://another.com/page'}
            style={{ fontFamily: 'monospace', resize: 'vertical' }}
          />
          <small style={{ color: '#718096' }}>
            https:// is added when no scheme is given. Precision/recall are computed against
            ground-truth labels; URLs without labels only contribute false positives.
          </small>
        </div>
      )}

      <fieldset
        style={{ borderWidth: 1, borderStyle: 'solid', borderColor: '#ccc', borderRadius: 4, padding: '0.6rem 0.9rem', margin: 0 }}
      >
        <legend style={{ fontWeight: 600, padding: '0 0.25rem' }}>Methods to compare</legend>
        <div style={{ display: 'flex', gap: '1.25rem', flexWrap: 'wrap' }}>
          {METHOD_OPTIONS.map((opt) => (
            <label key={opt.value} style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={methods.includes(opt.value)}
                onChange={(e) => toggleMethod(opt.value, e.target.checked)}
              />
              {opt.label}
            </label>
          ))}
        </div>
        {methods.length === 0 && (
          <small role="alert" style={{ display: 'block', marginTop: '0.4rem', color: '#b91c1c' }}>
            Select at least one method.
          </small>
        )}
        {aiDown && (
          <small
            role="status"
            style={{
              display: 'block',
              marginTop: '0.5rem',
              color: '#92400e',
              background: '#fffbeb',
              padding: '0.4rem 0.6rem',
              borderRadius: 4,
            }}
          >
            AI analysis is unavailable:{' '}
            {withAxeNote(aiStatus.message, 'Axe-Core still works.')} AI and Combined were unchecked.
          </small>
        )}
      </fieldset>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', opacity: usesAi ? 1 : 0.6 }}>
        <label htmlFor="model-select">AI model (used for the AI and Combined methods)</label>
        <select
          id="model-select"
          value={model}
          onChange={(e) => setModel(e.target.value as AiModel)}
          disabled={!usesAi}
          style={{ padding: '0.4rem 0.6rem' }}
        >
          <option value="gpt-6-luna">GPT-6 Luna</option>
          <option value="gpt-4o-mini">GPT-4o mini</option>
          <option value="gpt-4o">GPT-4o</option>
        </select>
      </div>

      {error && (
        <div
          role="alert"
          style={{ color: '#b91c1c', background: '#fef2f2', padding: '0.75rem 1rem', borderRadius: '6px' }}
        >
          {error}
        </div>
      )}

      <button
        type="submit"
        disabled={loading || methods.length === 0}
        style={{
          alignSelf: 'flex-start',
          padding: '0.5rem 1.25rem',
          background: '#1a7f64',
          color: '#fff',
          border: 'none',
          borderRadius: '6px',
          fontWeight: 600,
          cursor: loading ? 'progress' : 'pointer',
        }}
      >
        {loading ? 'Starting…' : 'Start Benchmark'}
      </button>
      <p style={{ margin: 0, color: '#718096', fontSize: '0.85rem' }}>
        Each page is scanned once per selected method, so a run can take several minutes.
        You can leave the results page and come back later from the Benchmark list.
      </p>
    </form>
  )
}

export default BenchmarkForm
