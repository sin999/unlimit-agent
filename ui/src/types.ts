export interface Hypothesis {
  title: string
  reasoning: string
  next_steps: string[]
}

export type Severity = 'low' | 'medium' | 'high'

export interface IncidentAnalysis {
  category: string
  summary: string
  severity: Severity
  hypotheses: Hypothesis[]
}
