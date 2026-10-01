import { Analysis, Classification } from '@/lib/analysis';
export function ClassificationResult({ result }: { result: Classification }) {
  return <div className="classification">
    <div className="review-meta"><strong>{result.primaryCategory.replaceAll('_', ' ')}</strong><span>{result.severity} severity</span><span>{result.sentiment.replaceAll('_', ' ')}</span><span>{Math.round(result.confidence * 100)}% model confidence</span></div>
    <p>{result.normalizedIssue || 'No specific issue identified.'}</p>
    <p className="muted">{result.isActionable ? 'Actionable feedback' : 'Not actionable'}{result.isLikelyBug ? ' · Possible bug' : ''}</p>
    {result.evidence.map((quote, i) => <blockquote key={i}>{quote}</blockquote>)}
    {result.tags.length > 0 && <p className="muted">Tags: {result.tags.join(', ')}</p>}
  </div>;
}
export function AnalysisResult({ analysis }: { analysis: Analysis | null }) {
  if (!analysis) return <p className="muted analysis-note">Not analyzed for the current text and model version.</p>;
  return <details className="analysis-details"><summary>Analysis · {analysis.status}{analysis.result ? ` · ${analysis.result.primaryCategory} · ${analysis.result.severity}` : ''}</summary>
    {analysis.result && <ClassificationResult result={analysis.result} />}
    {analysis.error && <p className="error">{analysis.error}</p>}
    {analysis.skipReason && <p className="muted">Skipped: {analysis.skipReason}</p>}
    {analysis.status === 'RUNNING' && <p role="status">Analysis in progress…</p>}
    <p className="muted">{analysis.responseModel ?? analysis.model} · {analysis.promptVersion} · {analysis.attempts} attempts{analysis.cachedFrom ? ' · Reused matching result' : ''}</p>
  </details>;
}
