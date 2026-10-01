import {Analysis,parseAnalysis} from './analysis';
export type AnalysisHistoryEntry={analysis:Analysis;inputHash:string;sourceText:string;sourceLanguage:string};
export function parseAnalysisHistory(value:unknown):AnalysisHistoryEntry[]{
 if(!Array.isArray(value))throw Error('Invalid analysis history.');
 return value.map(item=>{
  if(!item||typeof item!=='object')throw Error('Invalid history entry.');
  const analysis=parseAnalysis(item.analysis);
  if(!analysis||!Number.isFinite(Date.parse(analysis.updatedAt))||typeof item.inputHash!=='string'||!/^[0-9a-f]{64}$/.test(item.inputHash)||typeof item.sourceText!=='string'||typeof item.sourceLanguage!=='string')throw Error('Invalid history entry.');
  return {analysis,inputHash:item.inputHash,sourceText:item.sourceText,sourceLanguage:item.sourceLanguage};
 });
}
