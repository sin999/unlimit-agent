import { useState } from 'react'
import type { IncidentAnalysis } from './types'
import { analyzeIncident } from './api/client'
import AnalysisResult from './components/AnalysisResult'

export default function App() {
  const [description, setDescription] = useState('')
  const [analysis, setAnalysis] = useState<IncidentAnalysis | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!description.trim()) return
    setLoading(true)
    setError(null)
    setAnalysis(null)
    try {
      setAnalysis(await analyzeIncident(description.trim()))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An unexpected error occurred')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="bg-white border-b border-slate-200 px-6 py-4">
        <div className="max-w-3xl mx-auto">
          <h1 className="text-xl font-semibold text-slate-800">Unlimit Agent</h1>
          <p className="text-sm text-slate-500 mt-0.5">AI-powered incident triage</p>
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-6 py-8 space-y-4">
        <form onSubmit={handleSubmit} className="bg-white rounded-xl border border-slate-200 p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Incident description
            </label>
            <textarea
              value={description}
              onChange={e => setDescription(e.target.value)}
              rows={5}
              maxLength={2000}
              placeholder="Describe the incident — affected services, symptoms, error messages..."
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 resize-none"
            />
            <p className="text-xs text-slate-400 text-right mt-1">{description.length} / 2000</p>
          </div>

          <button
            type="submit"
            disabled={loading || description.trim().length === 0}
            className="w-full rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {loading ? 'Analyzing…' : 'Analyze incident'}
          </button>
        </form>

        {error && (
          <div className="rounded-xl border border-red-200 bg-red-50 px-5 py-4 text-sm text-red-700">
            {error}
          </div>
        )}

        {analysis && <AnalysisResult analysis={analysis} />}
      </main>
    </div>
  )
}
