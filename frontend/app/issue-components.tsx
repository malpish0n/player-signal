import { Evidence, Metrics, Snapshot, growth } from '@/lib/issues';
export function IssueMetrics({metrics:m}:{metrics:Metrics}) {return <>
  <div className="issue-metrics"><div><strong>{m.mentionCount}</strong><span>Review mentions</span></div><div><strong>{m.severityScore.toFixed(0)} / 100</strong><span>Mean severity</span></div><div><strong>{Math.round(m.negativeRatio*100)}%</strong><span>Negative classifications</span></div><div><strong>{Math.round(m.confidence*100)}%</strong><span>Mean model confidence</span></div></div>
  <p>{m.recentMentions} mentions in latest 7 days · {m.previousMentions} in previous 7 days · {growth(m)}</p>
  <p className="muted">First seen {new Date(m.firstSeenAt).toLocaleDateString()} · Last seen {new Date(m.lastSeenAt).toLocaleDateString()}</p>
</>;}
export function SnapshotInfo({snapshot:s}:{snapshot:Snapshot}) {return <div className="panel compact">
  <p>{s.builtAt?`Calculated ${new Date(s.builtAt).toLocaleString()} · ${s.clusteredReviews} clustered reviews`:'No issue snapshot yet.'}</p>
  {s.stale && s.builtAt && <p className="warning" role="status">Source analyses changed. These results describe an older snapshot. Rebuild to refresh.</p>}
  <p className="muted">{s.eligibleReviews} current actionable analyses · {s.algorithm} · Similarity threshold {s.threshold}</p>
  <p className="muted">{s.algorithm.startsWith("ollama-")?"Local model embeddings group related wording within the same category. Semantic matches still need human review.":"Local lexical grouping within the same category. Similar wording can group together; different paraphrases may remain separate."} Single mentions are tentative. Metrics cover this imported sample, not all players.</p>
  <details><summary>How metrics are calculated</summary><p className="muted">Each review counts once. Severity is the mean of LOW=25, MEDIUM=50, HIGH=75, CRITICAL=100. Negative ratio uses NEGATIVE and VERY_NEGATIVE classifications. Confidence is an uncalibrated model estimate. Growth compares two adjacent 7-day windows ending at the calculation time; no percentage is shown with a zero baseline. Dates use original Steam review creation time.</p></details>
</div>;}
export function IssueEvidence({evidence}:{evidence:Evidence[]}) {return <>{evidence.map(e=><article className="review" key={e.analysisId}><div className="review-meta"><strong>Source #{e.steamRecommendationId}</strong><span>{e.language}</span><span>{Math.round(e.similarity*100)}% vector similarity</span></div><p className="review-text">{e.sourceText}</p><div className="classification"><strong>Extracted evidence</strong>{e.classification.evidence.map((quote,i)=><blockquote key={i}>{quote}</blockquote>)}</div><p className="muted">{e.model} · {e.promptVersion} · Analysis {e.analysisId}</p></article>)}</>;}
