/** Severity strings from the backend are lowercase axe impact levels; normalise defensively. */
export const SEVERITY_ORDER = ['critical', 'serious', 'moderate', 'minor'] as const

export function normaliseSeverity(severity: string | null | undefined): string {
  const s = (severity ?? '').trim().toLowerCase()
  return (SEVERITY_ORDER as readonly string[]).includes(s) ? s : 'unknown'
}

export function severityRank(severity: string | null | undefined): number {
  const idx = (SEVERITY_ORDER as readonly string[]).indexOf(normaliseSeverity(severity))
  return idx === -1 ? SEVERITY_ORDER.length : idx
}
