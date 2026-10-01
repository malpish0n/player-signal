export type Classification = { sentiment: string; primaryCategory: string; severity: string; confidence: number; normalizedIssue: string; isActionable: boolean; isLikelyBug: boolean; evidence: string[]; tags: string[] };
export type Analysis = { id: string; status: string; provider: string; model: string; promptVersion: string; responseModel: string | null; attempts: number; result: Classification | null; error: string | null; skipReason: string | null; cachedFrom: string | null; updatedAt: string };
export type AnalysisRun = { id: string; status: string; processed: number; succeeded: number; skipped: number; failed: number; cached: number; error: string | null };
export type AnalysisStatus = { available: boolean; provider: string; model: string; promptVersion: string; maxReviewsPerRun: number; counts: Record<'total'|'pending'|'running'|'succeeded'|'skipped'|'failed', number>; latestRun: AnalysisRun | null };
function obj(v: unknown): Record<string, unknown> { if (!v || typeof v !== 'object' || Array.isArray(v)) throw Error('Invalid analysis response.'); return v as Record<string, unknown>; }
function str(v: unknown): string { if(typeof v !== 'string') throw Error('Invalid analysis text.'); return v; }
function nullable(v: unknown) { return v === null ? null : str(v); }
function count(v: unknown): number { if(typeof v !== 'number' || !Number.isSafeInteger(v) || v < 0) throw Error('Invalid analysis count.'); return v; }
function bool(v: unknown): boolean { if(typeof v !== 'boolean') throw Error('Invalid analysis flag.'); return v; }
function choice(v: unknown, values: string[]) { const s=str(v); if(!values.includes(s)) throw Error('Unknown analysis status or category.'); return s; }
function strings(v: unknown): string[] { if(!Array.isArray(v)) throw Error('Invalid analysis list.'); return v.map(str); }
export function parseClassification(v: unknown): Classification {
  const d=obj(v);
  if(typeof d.confidence !== 'number' || !Number.isFinite(d.confidence) || d.confidence < 0 || d.confidence > 1) throw Error('Invalid confidence.');
  return { sentiment:choice(d.sentiment,['VERY_NEGATIVE','NEGATIVE','MIXED','POSITIVE','VERY_POSITIVE']), primaryCategory:choice(d.primaryCategory,['BUG','PERFORMANCE','GAMEPLAY','UI_UX','MULTIPLAYER','BALANCE','CONTENT','CONTROLS','AUDIO','LOCALIZATION','POSITIVE','OTHER']), severity:choice(d.severity,['LOW','MEDIUM','HIGH','CRITICAL']), confidence:d.confidence, normalizedIssue:str(d.normalizedIssue), isActionable:bool(d.isActionable), isLikelyBug:bool(d.isLikelyBug), evidence:strings(d.evidence), tags:strings(d.tags) };
}
export function parseAnalysis(v: unknown): Analysis | null {
  if(v == null) return null;
  const d=obj(v); const status=choice(d.status,['RUNNING','SUCCEEDED','FAILED','SKIPPED']);
  const result=d.result === null ? null : parseClassification(d.result);
  if((status === 'SUCCEEDED') !== (result !== null)) throw Error('Inconsistent analysis result.');
  return {id:str(d.id),status,provider:str(d.provider),model:str(d.model),promptVersion:str(d.promptVersion),responseModel:nullable(d.responseModel),attempts:count(d.attempts),result,error:nullable(d.error),skipReason:nullable(d.skipReason),cachedFrom:nullable(d.cachedFrom),updatedAt:str(d.updatedAt)};
}
export function parseAnalysisRun(v: unknown): AnalysisRun {
  const d=obj(v); return {id:str(d.id),status:choice(d.status,['RUNNING','COMPLETED','PARTIAL','COMPLETED_WITH_ERRORS','FAILED']),processed:count(d.processed),succeeded:count(d.succeeded),skipped:count(d.skipped),failed:count(d.failed),cached:count(d.cached),error:nullable(d.error)};
}
export function parseAnalysisStatus(v: unknown): AnalysisStatus {
  const d=obj(v), c=obj(d.counts);
  return {available:bool(d.available),provider:str(d.provider),model:str(d.model),promptVersion:str(d.promptVersion),maxReviewsPerRun:count(d.maxReviewsPerRun),counts:{total:count(c.total),pending:count(c.pending),running:count(c.running),succeeded:count(c.succeeded),skipped:count(c.skipped),failed:count(c.failed)},latestRun:d.latestRun===null ? null : parseAnalysisRun(d.latestRun)};
}
export function parseDemo(v: unknown) {
  const d=obj(v); if(d.mode !== 'SYNTHETIC_DEMO' || !Array.isArray(d.reviews)) throw Error('Invalid demo.');
  return {description:str(d.description),reviews:d.reviews.map(v => {const r=obj(v);return {sourceId:str(r.sourceId),sourceText:str(r.sourceText),model:str(r.model),skippedReason:nullable(r.skippedReason),result:r.result===null ? null : parseClassification(r.result)};})};
}
