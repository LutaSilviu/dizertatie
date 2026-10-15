const STORAGE_KEY_API_KEY = 'apiKey'
const STORAGE_KEY_MODEL = 'model'
const DEFAULT_MODEL = 'gpt-4o-mini'

export interface Settings {
  apiKey: string
  model: string
  setApiKey: (key: string) => void
  setModel: (model: string) => void
}

export function getApiKey(): string {
  return sessionStorage.getItem(STORAGE_KEY_API_KEY) ?? ''
}

export function getModel(): string {
  return sessionStorage.getItem(STORAGE_KEY_MODEL) ?? DEFAULT_MODEL
}

export function setApiKey(key: string): void {
  sessionStorage.setItem(STORAGE_KEY_API_KEY, key)
}

export function setModel(model: string): void {
  sessionStorage.setItem(STORAGE_KEY_MODEL, model)
}

export function useSettings(): Settings {
  return {
    apiKey: getApiKey(),
    model: getModel(),
    setApiKey,
    setModel,
  }
}
