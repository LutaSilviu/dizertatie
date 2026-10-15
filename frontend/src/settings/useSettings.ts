import { useState } from 'react'

const API_KEY_STORAGE_KEY = 'apiKey'
const AI_MODEL_STORAGE_KEY = 'aiModel'
const DEFAULT_AI_MODEL = 'gpt-4o-mini'

export type AiModel = 'gpt-4o' | 'gpt-4o-mini'

export interface Settings {
  apiKey: string
  aiModel: AiModel
  setApiKey: (key: string) => void
  setAiModel: (model: AiModel) => void
}

function readApiKey(): string {
  return sessionStorage.getItem(API_KEY_STORAGE_KEY) ?? ''
}

function readAiModel(): AiModel {
  const stored = sessionStorage.getItem(AI_MODEL_STORAGE_KEY)
  if (stored === 'gpt-4o' || stored === 'gpt-4o-mini') {
    return stored
  }
  return DEFAULT_AI_MODEL
}

export function useSettings(): Settings {
  const [apiKey, setApiKeyState] = useState<string>(readApiKey)
  const [aiModel, setAiModelState] = useState<AiModel>(readAiModel)

  const setApiKey = (key: string) => {
    sessionStorage.setItem(API_KEY_STORAGE_KEY, key)
    setApiKeyState(key)
  }

  const setAiModel = (model: AiModel) => {
    sessionStorage.setItem(AI_MODEL_STORAGE_KEY, model)
    setAiModelState(model)
  }

  return { apiKey, aiModel, setApiKey, setAiModel }
}
