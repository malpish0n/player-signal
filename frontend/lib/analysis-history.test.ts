import {expect,it} from 'vitest';
import {parseAnalysisHistory} from './analysis-history';
it('accepts an empty real history without fixture substitution',()=>{expect(parseAnalysisHistory([])).toEqual([]);});
it('rejects malformed snapshots and missing analysis records',()=>{for(const value of [{},[null],[{analysis:null,inputHash:'a'.repeat(64),sourceText:'text',sourceLanguage:'english'}]])expect(()=>parseAnalysisHistory(value)).toThrow();});
