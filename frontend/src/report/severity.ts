/**
 * The backend severity score is 0–100 where HIGHER IS BETTER (100 = no findings).
 * See backend SeverityScoringService.
 */
export type ScoreRating = 'good' | 'fair' | 'poor'

export function rateScore(score: number): ScoreRating {
  if (score >= 90) return 'good'
  if (score >= 70) return 'fair'
  return 'poor'
}

export const RATING_LABELS: Record<ScoreRating, string> = {
  good: 'Good',
  fair: 'Needs work',
  poor: 'Poor',
}

export const RATING_COLORS: Record<ScoreRating, string> = {
  good: '#276749',
  fair: '#9c4221',
  poor: '#c53030',
}

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
