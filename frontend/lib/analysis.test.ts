import { expect, it } from 'vitest';
import { parseAnalysis, parseClassification, parseDemo, parseAnalysisStatus } from './analysis';
const result = {sentiment:'NEGATIVE',primaryCategory:'BUG',severity:'HIGH',confidence:.55,normalizedIssue:'Lobby crash',isActionable:true,isLikelyBug:true,evidence:['It crashes.'],tags:['crash']};
it('keeps fractional confidence and original evidence', () => {
  expect(parseClassification(result).confidence).toBe(.55);
  expect(parseClassification(result).evidence).toEqual(['It crashes.']);
});
it('rejects invalid categories, confidence and booleans', () => {
  for(const patch of [{confidence:101},{confidence:NaN},{primaryCategory:'INVENTED'},{isActionable:'false'}]) expect(() => parseClassification({...result,...patch})).toThrow();
});
it('distinguishes absent, failed and successful results', () => {
  expect(parseAnalysis(null)).toBeNull();
  const analysis={id:'a',status:'FAILED',provider:'openai',model:'model',promptVersion:'v1',responseModel:null,attempts:3,result:null,error:'Invalid output',skipReason:null,cachedFrom:null,updatedAt:'2026-10-01T00:00:00Z'};
  expect(parseAnalysis(analysis)?.error).toBe('Invalid output');
  expect(() => parseAnalysis({...analysis,result})).toThrow('Inconsistent');
  expect(() => parseAnalysis({...analysis,status:'SUCCEEDED'})).toThrow('Inconsistent');
});
it('requires explicit synthetic mode in demo data', () => {
  expect(() => parseDemo({mode:'LIVE',reviews:[]})).toThrow();
  expect(parseDemo({mode:'SYNTHETIC_DEMO',description:'Example',reviews:[]}).reviews).toEqual([]);
});
it('keeps unavailable configuration distinct from empty data', () => {
  const status=parseAnalysisStatus({available:false,provider:'openai',model:'m',promptVersion:'v1',maxReviewsPerRun:25,counts:{total:1000,pending:1000,running:0,succeeded:0,skipped:0,failed:0},latestRun:null});
  expect(status.available).toBe(false); expect(status.counts.pending).toBe(1000);
});
