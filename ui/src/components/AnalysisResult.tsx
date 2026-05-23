import type { IncidentAnalysis, Severity } from '../types'

const severityStyles: Record<Severity, string> = {
  high:   'bg-red-100 text-red-700',
  medium: 'bg-amber-100 text-amber-700',
  low:    'bg-green-100 text-green-700',
}

export default function AnalysisResult({ analysis }: { analysis: IncidentAnalysis }) {
  const badgeStyle = severityStyles[analysis.severity] ?? severityStyles.medium

  return (
    <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Category</p>
          <p className="mt-1 text-base font-semibold text-slate-800">{analysis.category}</p>
        </div>
        <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold capitalize ${badgeStyle}`}>
          {analysis.severity}
        </span>
      </div>

      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Summary</p>
        <p className="mt-1 text-sm text-slate-700">{analysis.summary}</p>
      </div>

      {analysis.hypotheses.length > 0 && (
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-slate-400 mb-3">Hypotheses</p>
          <ol className="space-y-3">
            {analysis.hypotheses.map((h, i) => (
              <li key={i} className="rounded-lg border border-slate-100 bg-slate-50 p-4">
                <p className="text-sm font-semibold text-slate-800">
                  <span className="mr-2 text-slate-400">{i + 1}.</span>{h.title}
                </p>
                <p className="mt-1.5 text-sm text-slate-600">{h.reasoning}</p>
                {h.next_steps.length > 0 && (
                  <ul className="mt-2.5 space-y-1.5">
                    {h.next_steps.map((step, j) => (
                      <li key={j} className="flex items-start gap-2 text-sm text-slate-600">
                        <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-400" />
                        {step}
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ol>
        </div>
      )}
    </div>
  )
}
