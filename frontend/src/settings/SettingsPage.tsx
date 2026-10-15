import { useState } from 'react'
import { useSettings, type AiModel } from './useSettings'
import { apiFetch } from './apiClient'

export function SettingsPage() {
  const { apiKey, aiModel, setApiKey, setAiModel } = useSettings()

  const [draftKey, setDraftKey] = useState<string>(apiKey)
  const [draftModel, setDraftModel] = useState<AiModel>(aiModel)
  const [saved, setSaved] = useState(false)
  const [connectionStatus, setConnectionStatus] = useState<'idle' | 'checking' | 'ok' | 'error'>('idle')

  const handleSave = () => {
    setApiKey(draftKey)
    setAiModel(draftModel)
    setSaved(true)
    setConnectionStatus('idle')
    setTimeout(() => setSaved(false), 3000)
  }

  const handleTestConnection = async () => {
    setConnectionStatus('checking')
    try {
      const res = await apiFetch('/api/health')
      setConnectionStatus(res.ok ? 'ok' : 'error')
    } catch {
      setConnectionStatus('error')
    }
  }

  return (
    <div style={{ maxWidth: 480, margin: '2rem auto', padding: '0 1rem' }}>
      <h1>Settings</h1>
      <p>Settings page — implementation coming soon.</p>

      <div style={{ marginBottom: '1.5rem' }}>
        <label htmlFor="api-key" style={{ display: 'block', marginBottom: '0.25rem', fontWeight: 600 }}>
          API Key
        </label>
        <input
          id="api-key"
          type="password"
          value={draftKey}
          onChange={(e) => {
            setDraftKey(e.target.value)
            setSaved(false)
          }}
          placeholder="Enter your API key"
          style={{ width: '100%', padding: '0.5rem', boxSizing: 'border-box' }}
        />
      </div>

      <fieldset style={{ marginBottom: '1.5rem', border: '1px solid #ccc', padding: '0.75rem 1rem' }}>
        <legend style={{ fontWeight: 600 }}>AI Model</legend>

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
            Connection failed
          </span>
        )}
      </div>
    </div>
  )
}

export default SettingsPage
