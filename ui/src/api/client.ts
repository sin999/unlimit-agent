import type { IncidentAnalysis } from '../types'

export async function analyzeIncident(description: string): Promise<IncidentAnalysis> {
  const response = await fetch('/api/v1/incidents/analyze', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ description }),
  })

  if (!response.ok) {
    const body = await response.json().catch(() => ({ error: 'Request failed' }))
    throw new Error(body.error ?? 'Request failed')
  }

  return response.json()
}
