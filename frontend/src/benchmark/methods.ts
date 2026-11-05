export type Method = 'AXE' | 'AI' | 'COMBINED'

export const METHODS: Method[] = ['AXE', 'AI', 'COMBINED']

export const METHOD_LABELS: Record<Method, string> = {
  AXE: 'Axe-Core',
  AI: 'AI',
  COMBINED: 'Combined',
}

/**
 * Methods a run was configured with. Older backends do not send `methods`; they always ran
 * all three, which is also the backend default.
 */
export function runMethods(methods: string[] | null | undefined): Method[] {
  if (!Array.isArray(methods) || methods.length === 0) return METHODS
  const upper = methods.map((m) => String(m).toUpperCase())
  const known = METHODS.filter((m) => upper.includes(m))
  return known.length > 0 ? known : METHODS
}

export function formatMethods(methods: string[] | null | undefined): string {
  return runMethods(methods).map((m) => METHOD_LABELS[m]).join(', ')
}
