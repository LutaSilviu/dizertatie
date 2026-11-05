import { useEffect, useState } from 'react'
import { apiFetch } from './apiClient'

/** Mirrors backend `GET /ai/status`. */
export type AiStatusCode = 'OK' | 'MISSING_KEY' | 'INVALID_KEY' | 'UNREACHABLE'

export interface AiStatus {
  status: AiStatusCode
  message: string
}

const DEFAULT_MESSAGES: Record<AiStatusCode, string> = {
  OK: 'AI analysis is available.',
  MISSING_KEY: 'OpenAI API key is not configured. Set OPENAI_API_KEY in .env and restart the backend.',
  INVALID_KEY: 'The OpenAI API key was rejected. Check OPENAI_API_KEY in .env and restart the backend.',
  UNREACHABLE: 'The OpenAI API could not be reached from the backend.',
}

function isStatusCode(v: unknown): v is AiStatusCode {
  return v === 'OK' || v === 'MISSING_KEY' || v === 'INVALID_KEY' || v === 'UNREACHABLE'
}

/**
 * Fetches the AI status. Resolves to `null` when it cannot be determined (e.g. an older
 * backend without the endpoint, or the backend is down) so callers do not show false warnings.
 */
export async function fetchAiStatus(): Promise<AiStatus | null> {
  try {
    const res = await apiFetch('/api/ai/status')
    if (!res.ok) return null
    const body = (await res.json()) as Partial<AiStatus>
    if (!isStatusCode(body.status)) return null
    const message = typeof body.message === 'string' && body.message.trim()
      ? body.message.trim()
      : DEFAULT_MESSAGES[body.status]
    return { status: body.status, message }
  } catch {
    return null
  }
}

// Fetched once per page load and shared by every component that asks.
let cached: Promise<AiStatus | null> | null = null

export function getAiStatusOnce(): Promise<AiStatus | null> {
  if (!cached) {
    cached = fetchAiStatus().then((s) => {
      // Do not cache an undetermined result, so a later page can retry.
      if (s === null) cached = null
      return s
    })
  }
  return cached
}

/** `undefined` while loading, `null` when unknown, otherwise the status. */
export function useAiStatus(): AiStatus | null | undefined {
  const [status, setStatus] = useState<AiStatus | null | undefined>(undefined)
  useEffect(() => {
    let cancelled = false
    getAiStatusOnce().then((s) => {
      if (!cancelled) setStatus(s)
    })
    return () => {
      cancelled = true
    }
  }, [])
  return status
}

/** Appends `note` (e.g. "Axe-Core still works.") unless the message already mentions Axe. */
export function withAxeNote(message: string, note: string): string {
  const trimmed = message.trim()
  if (/\baxe\b|axe-core/i.test(trimmed)) return trimmed
  const sep = /[.!?]$/.test(trimmed) ? ' ' : '. '
  return `${trimmed}${sep}${note}`
}

export function isAiUnavailable(status: AiStatus | null | undefined): status is AiStatus {
  return !!status && status.status !== 'OK'
}
