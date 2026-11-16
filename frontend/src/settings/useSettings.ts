import { useState } from 'react'

const AI_MODEL_STORAGE_KEY = 'aiModel'
const AI_MODEL_SAVED_AT_KEY = 'aiModelSavedAt'

/**
 * When the default model was last saved in Settings (ms epoch, 0 if never). The Analyzer uses
 * it to let a newer Settings save win over a model restored from its own session state.
 */
export function readAiModelSavedAt(): number {
  try {
    const n = Number(localStorage.getItem(AI_MODEL_SAVED_AT_KEY))
    return Number.isFinite(n) ? n : 0
  } catch {
    return 0
  }
}
const DEFAULT_AI_MODEL = 'gpt-6-luna'

// Must match backend AiModel ids.
export type AiModel = 'gpt-6-luna' | 'gpt-4o' | 'gpt-4o-mini'

export interface Settings {
  aiModel: AiModel
  setAiModel: (model: AiModel) => void
}

function readAiModel(): AiModel {
  try {
    // localStorage so the choice survives new tabs and browser restarts.
    const stored = localStorage.getItem(AI_MODEL_STORAGE_KEY)
    if (stored === 'gpt-6-luna' || stored === 'gpt-4o' || stored === 'gpt-4o-mini') {
      return stored
    }
  } catch {
    // Storage unavailable (private mode, blocked site data): fall back to the default.
  }
  return DEFAULT_AI_MODEL
}

export function useSettings(): Settings {
  const [aiModel, setAiModelState] = useState<AiModel>(readAiModel)

  const setAiModel = (model: AiModel) => {
    try {
      localStorage.setItem(AI_MODEL_STORAGE_KEY, model)
      localStorage.setItem(AI_MODEL_SAVED_AT_KEY, String(Date.now()))
    } catch {
      // Ignore: the choice still applies for this page view.
    }
    setAiModelState(model)
  }

  return { aiModel, setAiModel }
}
