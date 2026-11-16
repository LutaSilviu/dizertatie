import { useState } from 'react'
import { useSettings, type AiModel } from './useSettings'
import { apiFetch, readApiError } from './apiClient'
import { fetchAiStatus, withAxeNote, type AiStatus } from './aiStatus'

type BackendCheck =
  | { state: 'ok' }
  | { state: 'invalid-key' }
  | { state: 'error'; detail: string | null }

type AiCheck = { state: 'known'; status: AiStatus } | { state: 'unknown' } | { state: 'skipped' }

interface CheckResult {
  backend: BackendCheck
  ai: AiCheck
}

const MODEL_OPTIONS: { value: AiModel; label: string }[] = [
  { value: 'gpt-6-luna', label: 'GPT-6 Luna (recommended)' },
  { value: 'gpt-4o-mini', label: 'GPT-4o mini (faster, cheaper)' },
  { value: 'gpt-4o', label: 'GPT-4o (more capable)' },
]

const AI_STATUS_TITLES: Record<AiStatus['status'], string> = {
  OK: 'AI analysis available',
  MISSING_KEY: 'AI analysis unavailable: no OpenAI API key',
  INVALID_KEY: 'AI analysis unavailable: OpenAI API key rejected',
  UNREACHABLE: 'AI analysis unavailable: OpenAI not reachable',
}

async function checkBackend(): Promise<BackendCheck> {
  try {
    // /labels requires the API key (unlike /health), so this also validates the key.
    const res = await apiFetch('/api/labels')
    if (res.ok) return { state: 'ok' }
    if (res.status === 401) return { state: 'invalid-key' }
    return { state: 'error', detail: `HTTP ${res.status}: ${await readApiError(res)}` }
  } catch {
    return { state: 'error', detail: null }
  }
}

function StatusLine({ ok, title, children }: { ok: boolean; title: string; children?: React.ReactNode }) {
  return (
    <li style={{ display: 'flex', gap: '0.5rem', alignItems: 'flex-start' }}>
      <span aria-hidden="true" style={{ color: ok ? '#276749' : '#c53030', fontWeight: 700 }}>
        {ok ? '✓' : '✗'}
      </span>
      <span>
        <strong style={{ color: ok ? '#276749' : '#c53030' }}>{title}</strong>
        {children && <span style={{ display: 'block', color: '#4a5568', fontSize: '0.9rem' }}>{children}</span>}
      </span>
    </li>
  )
}

export function SettingsPage() {
  const { aiModel, setAiModel } = useSettings()

  const [draftModel, setDraftModel] = useState<AiModel>(aiModel)
  const [saved, setSaved] = useState(false)
  const [checking, setChecking] = useState(false)
  const [result, setResult] = useState<CheckResult | null>(null)

  const handleSave = () => {
    setAiModel(draftModel)
    setSaved(true)
    setTimeout(() => setSaved(false), 3000)
  }

  const handleTestConnection = async () => {
    setChecking(true)
    setResult(null)
    const backend = await checkBackend()
    let ai: AiCheck = { state: 'skipped' }
    if (backend.state === 'ok') {
      const status = await fetchAiStatus()
      ai = status ? { state: 'known', status } : { state: 'unknown' }
    }
    setResult({ backend, ai })
    setChecking(false)
  }

  return (
    <div className="app-page">
      <h1>Settings</h1>
      <p>The API proxy automatically uses <code>APP_API_KEY</code> from <code>.env</code>.</p>
      <p style={{ color: '#4a5568', fontSize: '0.9rem' }}>
        The model chosen here is the default for AI and Combined scans and for new benchmark runs.
      </p>

      <div style={{ maxWidth: 560 }}>
        <fieldset
          style={{ marginBottom: '1.5rem', borderWidth: 1, borderStyle: 'solid', borderColor: '#ccc', borderRadius: 4, padding: '0.75rem 1rem' }}
        >
          <legend style={{ fontWeight: 600 }}>AI Model</legend>
          {MODEL_OPTIONS.map((opt) => (
            <label
              key={opt.value}
              style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}
            >
              <input
                type="radio"
                name="ai-model"
                value={opt.value}
                checked={draftModel === opt.value}
                onChange={() => setDraftModel(opt.value)}
              />
              {opt.label}
            </label>
          ))}
        </fieldset>

        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <button onClick={handleSave} style={{ padding: '0.5rem 1.25rem', cursor: 'pointer' }}>
            Save
          </button>

          <button
            onClick={handleTestConnection}
            disabled={checking}
            style={{ padding: '0.5rem 1.25rem', cursor: 'pointer' }}
          >
            {checking ? 'Checking…' : 'Test connection'}
          </button>

          {saved && (
            <span style={{ color: 'green' }} role="status">
              Saved!
            </span>
          )}
        </div>

        {result && (
          <ul role="status" style={{ listStyle: 'none', padding: 0, margin: '1rem 0 0', display: 'grid', gap: '0.5rem' }}>
            {result.backend.state === 'ok' && <StatusLine ok title="Backend API connected" />}
            {result.backend.state === 'invalid-key' && (
              <StatusLine ok={false} title="Backend API key rejected">
                Check <code>APP_API_KEY</code> in <code>.env</code> and restart the stack.
              </StatusLine>
            )}
            {result.backend.state === 'error' && (
              <StatusLine ok={false} title="Backend API not reachable">
                Check that the backend (or the Docker services) is running.
                {result.backend.detail && <> ({result.backend.detail})</>}
              </StatusLine>
            )}

            {result.ai.state === 'known' && (
              <StatusLine ok={result.ai.status.status === 'OK'} title={AI_STATUS_TITLES[result.ai.status.status]}>
                {result.ai.status.status === 'OK'
                  ? null
                  : withAxeNote(result.ai.status.message, 'Axe-Core scans still work.')}
              </StatusLine>
            )}
            {result.ai.state === 'unknown' && (
              <StatusLine ok={false} title="AI status unknown">
                The backend did not report AI availability (it may be an older version without
                <code> /ai/status</code>).
              </StatusLine>
            )}
            {result.ai.state === 'skipped' && (
              <StatusLine ok={false} title="AI status not checked">
                The backend must be reachable first.
              </StatusLine>
            )}
          </ul>
        )}
      </div>
    </div>
  )
}

export default SettingsPage
