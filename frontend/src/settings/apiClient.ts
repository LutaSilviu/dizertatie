const API_KEY_STORAGE_KEY = 'apiKey'

function getApiKey(): string {
  return sessionStorage.getItem(API_KEY_STORAGE_KEY) ?? ''
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const key = getApiKey()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string> | undefined),
  }
  if (key) {
    headers['X-API-Key'] = key
  }
  return fetch(path, { ...init, headers })
}
