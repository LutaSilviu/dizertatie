import { getApiKey } from './useSettings'

export interface FetchOptions extends RequestInit {
  apiKey?: string
}

export async function apiFetch<T = unknown>(
  path: string,
  options: FetchOptions = {}
): Promise<T> {
  const { apiKey, ...rest } = options
  const key = apiKey ?? getApiKey()

  const response = await fetch(path, {
    ...rest,
    headers: {
      'Content-Type': 'application/json',
      ...(key ? { 'X-API-Key': key } : {}),
      ...(rest.headers ?? {}),
    },
  })

  if (!response.ok) {
    let message = `Request failed: ${response.status} ${response.statusText}`
    try {
      const body = await response.json() as { error?: string; message?: string }
      message = body.error ?? body.message ?? message
    } catch {
      // ignore parse errors
    }
    throw new Error(message)
  }

  return response.json() as Promise<T>
}
