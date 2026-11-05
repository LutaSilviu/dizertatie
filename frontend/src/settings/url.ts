/**
 * Shortens a URL in the middle so the host and the end of the path (e.g. the file name) stay
 * visible. Show the full URL in a `title` tooltip alongside it.
 */
export function truncateMiddle(text: string, max = 60): string {
  if (text.length <= max) return text
  const display = text.replace(/^https?:\/\//, '')
  if (display.length <= max) return display
  const tail = Math.floor((max - 1) * 0.55)
  const head = max - 1 - tail
  return `${display.slice(0, head)}…${display.slice(display.length - tail)}`
}

/**
 * Normalises user-typed URLs: trims, prefixes `https://` when no scheme is given
 * (`example.com`, `www.wikipedia.org`, `localhost:8080/x`), and accepts only http(s).
 * Returns `null` for anything else (e.g. `javascript:`, `ftp:`, garbage).
 */
export function normalizeUrl(input: string): string | null {
  const value = input.trim()
  if (!value) return null

  // Explicit scheme ("scheme://" or "scheme:" like javascript:/mailto:), but not "host:port".
  const schemeMatch = /^([a-z][a-z0-9+.-]*):/i.exec(value)
  const looksLikeHostPort = /^[^/:]+:\d+(\/|$)/.test(value)
  const bare = value.replace(/^\/\//, '')
  // Local servers rarely have TLS, so default them to http.
  const isLocal = /^(localhost|127\.0\.0\.1|\[::1\])(:|\/|$)/i.test(bare)
  const candidate = schemeMatch && !looksLikeHostPort
    ? value
    : `${isLocal ? 'http' : 'https'}://${bare}`

  try {
    const url = new URL(candidate)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
    if (!url.hostname) return null
    // A bare word like "foo" has no dot; allow it only for localhost.
    if (!url.hostname.includes('.') && url.hostname !== 'localhost' && !url.hostname.includes(':')) {
      return null
    }
    return candidate === value ? value : url.toString()
  } catch {
    return null
  }
}
