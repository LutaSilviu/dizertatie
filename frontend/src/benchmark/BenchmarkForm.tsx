import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiFetch } from '../settings/apiClient'
import { useSettings, AiModel } from '../settings/useSettings'

interface BenchmarkRunResponse {
  id: number
  status: string
}

function BenchmarkForm() {
  const navigate = useNavigate()
  const { aiModel } = useSettings()

  const [useBuiltinSet, setUseBuiltinSet] = useState(true)
  const [urlsText, setUrlsText] = useState('')
  const [model, setModel] = useState<AiModel>(aiModel)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    const urls = useBuiltinSet
      ? []
      : urlsText
          .split('\n')
          .map((u) => u.trim())
          .filter(Boolean)

    try {
      const response = await apiFetch('/api/benchmark/runs', {
        method: 'POST',
        body: JSON.stringify({ useBuiltinSet, urls, model }),
      })

      if (!response.ok) {
        const text = await response.text()
        throw new Error(text || `Request failed with status ${response.status}`)
      }

      const data: BenchmarkRunResponse = await response.json()
      navigate(`/benchmark/${data.id}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An unexpected error occurred')
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', maxWidth: '480px' }}>
      <div>
        <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={useBuiltinSet}
            onChange={(e) => setUseBuiltinSet(e.target.checked)}
          />
          Use built-in URL set
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
            placeholder="https://example.com&#10;https://another.com"
            style={{ fontFamily: 'monospace', resize: 'vertical' }}
          />
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
        <label htmlFor="model-select">Model</label>
        <select
          id="model-select"
          value={model}
          onChange={(e) => setModel(e.target.value as AiModel)}
        >
          <option value="gpt-4o-mini">gpt-4o-mini</option>
          <option value="gpt-4o">gpt-4o</option>
        </select>
      </div>

      {error && (
        <div role="alert" style={{ color: 'red' }}>
          {error}
        </div>
      )}

      <button type="submit" disabled={loading}>
        {loading ? 'Starting…' : 'Start Benchmark'}
      </button>
    </form>
  )
}

export default BenchmarkForm
