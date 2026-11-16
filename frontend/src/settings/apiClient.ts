export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  return fetch(path, { ...init, headers })
}

const STATUS_HINTS: Record<number, string> = {
  400: 'The request was rejected as invalid.',
  401: 'The API key was rejected. Check APP_API_KEY in .env and restart the stack.',
  403: 'Access denied.',
  404: 'Not found.',
  409: 'The resource is not ready yet.',
  500: 'The server hit an internal error.',
  502: 'The backend is unreachable, or the target site refused the request.',
  503: 'The backend is unavailable. Check that the Docker services are running.',
  504: 'The request timed out before the server answered.',
}

/**
 * Extracts a human-readable error message from a failed API response.
 *
 * Handles custom handler bodies (`{"error": "..."}`), Spring's default error body
 * (`{timestamp, status, error: "<reason phrase>", message, path}` — `message` is often empty),
 * ProblemDetail bodies (`{title, detail}`) and non-JSON bodies (e.g. nginx/Vite proxy error pages).
 */
export async function readApiError(res: Response): Promise<string> {
  return (await readApiErrorDetail(res)).message
}

export interface ApiErrorDetail {
  message: string
  /**
   * True when the message came from the backend itself (a specific explanation); false when
   * it is a generic status hint because the body was empty, a reason phrase only, or an
   * nginx/Vite HTML error page.
   */
  fromBackend: boolean
}

export async function readApiErrorDetail(res: Response): Promise<ApiErrorDetail> {
  const fallback: ApiErrorDetail = {
    message: STATUS_HINTS[res.status] ?? `Request failed (HTTP ${res.status}).`,
    fromBackend: false,
  }
  let text = ''
  try {
    text = await res.text()
  } catch {
    return fallback
  }
  if (!text) return fallback

  try {
    const body = JSON.parse(text) as Record<string, unknown>
    const str = (v: unknown) => (typeof v === 'string' && v.trim() ? v.trim() : null)
    const detail = str(body.detail) ?? str(body.message)
    if (detail) return { message: detail, fromBackend: true }
    // Spring's default error body carries `status`; its `error` is just the reason phrase.
    const isSpringDefault = typeof body.status === 'number'
    const error = str(body.error)
    if (error && !isSpringDefault) return { message: error, fromBackend: true }
    return fallback
  } catch {
    // Not JSON (HTML proxy error page, plain text); short plain text is still useful.
    const trimmed = text.trim()
    if (trimmed.length > 0 && trimmed.length < 300 && !trimmed.startsWith('<')) {
      return { message: trimmed, fromBackend: true }
    }
    return fallback
  }
}

/** Turns a thrown fetch error (network failure, abort) into a readable message. */
export function networkErrorMessage(err: unknown): string {
  if (err instanceof DOMException && err.name === 'AbortError') return 'Request cancelled.'
  if (err instanceof TypeError) {
    return 'Could not reach the API. Check that the backend is running.'
  }
  return err instanceof Error ? err.message : 'An unexpected error occurred.'
}

/** Downloads a file from the API (the proxy injects the API key) and saves it via a temporary link. */
export async function downloadFile(path: string, fallbackFilename: string): Promise<void> {
  const res = await apiFetch(path)
  if (!res.ok) {
    throw new Error(`Export failed: ${await readApiError(res)}`)
  }
  const disposition = res.headers.get('Content-Disposition') ?? ''
  const match = /filename="?([^";]+)"?/i.exec(disposition)
  const filename = match?.[1] ?? fallbackFilename

  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  // Revoking synchronously can cancel the download in some browsers.
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

/**
 * Formats a backend timestamp for display.
 *
 * `Instant` fields arrive with a `Z` suffix. `LocalDateTime` fields (e.g. benchmark `createdAt`)
 * arrive without an offset; the backend records them in UTC, so they are parsed as UTC.
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const hasOffset = /(Z|[+-]\d{2}:?\d{2})$/i.test(value)
  const date = new Date(hasOffset ? value : `${value}Z`)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
