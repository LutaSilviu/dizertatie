import { useState } from 'react'
import { useSettings, type AiModel } from './useSettings'
import { apiFetch } from './apiClient'

export function SettingsPage() {
  const { aiModel, setAiModel } = useSettings()

  const [draftModel, setDraftModel] = useState<AiModel>(aiModel)
  const [saved, setSaved] = useState(false)
  const [connectionStatus, setConnectionStatus] = useState<
    'idle' | 'checking' | 'ok' | 'invalid-key' | 'error'
  >('idle')

  const handleSave = () => {
    setAiModel(draftModel)
    setSaved(true)
    setConnectionStatus('idle')
    setTimeout(() => setSaved(false), 3000)
  }

  const handleTestConnection = async () => {
    setConnectionStatus('checking')
    try {
      const res = await apiFetch('/api/labels')
      setConnectionStatus(res.ok ? 'ok' : res.status === 401 ? 'invalid-key' : 'error')
    } catch {
      setConnectionStatus('error')
    }
  }

  return (
    <div style={{ maxWidth: 480, margin: '2rem auto', padding: '0 1rem' }}>
      <h1>Settings</h1>
      <p>The API proxy automatically uses <code>APP_API_KEY</code> from <code>.env</code>.</p>

      <fieldset style={{ marginBottom: '1.5rem', border: '1px solid #ccc', padding: '0.75rem 1rem' }}>
        <legend style={{ fontWeight: 600 }}>AI Model</legend>

        <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
          <input
            type="radio"
            name="ai-model"
            value="gpt-6-luna"
            checked={draftModel === 'gpt-6-luna'}
            onChange={() => setDraftModel('gpt-6-luna')}
          />
          GPT-6 Luna (recommended)
        </label>

        <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
          <input
            type="radio"
            name="ai-model"
            value="gpt-4o-mini"
            checked={draftModel === 'gpt-4o-mini'}
            onChange={() => setDraftModel('gpt-4o-mini')}
          />
          GPT-4o mini (faster, cheaper)
        </label>

        <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <input
            type="radio"
            name="ai-model"
            value="gpt-4o"
            checked={draftModel === 'gpt-4o'}
            onChange={() => setDraftModel('gpt-4o')}
          />
          GPT-4o (more capable)
        </label>
      </fieldset>

      <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
        <button
          onClick={handleSave}
          style={{ padding: '0.5rem 1.25rem', cursor: 'pointer' }}
        >
          Save
        </button>

        <button
          onClick={handleTestConnection}
          disabled={connectionStatus === 'checking'}
          style={{ padding: '0.5rem 1.25rem', cursor: 'pointer' }}
        >
          {connectionStatus === 'checking' ? 'Checking…' : 'Test connection'}
        </button>

        {saved && (
          <span style={{ color: 'green' }} role="status">
            Saved!
          </span>
        )}

        {connectionStatus === 'ok' && (
          <span style={{ color: 'green' }} role="status">
            Connected
          </span>
        )}

        {connectionStatus === 'error' && (
          <span style={{ color: 'red' }} role="status">
            Could not reach the API. Check that the Docker services are running.
          </span>
        )}

        {connectionStatus === 'invalid-key' && (
          <span style={{ color: 'red' }} role="status">
            API key rejected. Check <code>APP_API_KEY</code> in <code>.env</code> and restart the stack.
          </span>
        )}
      </div>
    </div>
  )
}

export default SettingsPage
