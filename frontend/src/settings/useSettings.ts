import { useState } from 'react'

const AI_MODEL_STORAGE_KEY = 'aiModel'
const DEFAULT_AI_MODEL = 'gpt-6-luna'

export type AiModel = 'gpt-6-luna' | 'gpt-4o' | 'gpt-4o-mini'

export interface Settings {
  aiModel: AiModel
  setAiModel: (model: AiModel) => void
}

function readAiModel(): AiModel {
  const stored = sessionStorage.getItem(AI_MODEL_STORAGE_KEY)
  if (stored === 'gpt-6-luna' || stored === 'gpt-4o' || stored === 'gpt-4o-mini') {
    return stored
  }
  return DEFAULT_AI_MODEL
}

export function useSettings(): Settings {
  const [aiModel, setAiModelState] = useState<AiModel>(readAiModel)

  const setAiModel = (model: AiModel) => {
    sessionStorage.setItem(AI_MODEL_STORAGE_KEY, model)
    setAiModelState(model)
  }

  return { aiModel, setAiModel }
}
