// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {AnalysisHistory} from './analysis-history';
const entry={analysis:{id:'old',status:'FAILED',provider:'openai',model:'old-model',promptVersion:'v1',responseModel:null,attempts:2,result:null,error:'Provider unavailable',skipReason:null,cachedFrom:null,updatedAt:'2026-10-01T10:00:00Z'},inputHash:'a'.repeat(64),sourceText:'Original text before the edit.',sourceLanguage:'english'};
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('loads only on expansion and shows original input plus version provenance',async()=>{
 const fetch=vi.fn().mockImplementation(async()=>Response.json([entry,{...entry,analysis:{...entry.analysis,id:'current',status:'SUCCEEDED',error:null,result:{sentiment:'NEGATIVE',primaryCategory:'BUG',severity:'HIGH',confidence:.9,normalizedIssue:'Earlier crash',isActionable:true,isLikelyBug:true,evidence:['Original text'],tags:[]}}}]));vi.stubGlobal('fetch',fetch);
 render(<AnalysisHistory gameId="game" reviewId="review" currentId="current"/>);expect(fetch).not.toHaveBeenCalled();
 await userEvent.click(screen.getByRole('button',{name:'View analysis history'}));await screen.findByText('Historical version');
 expect(fetch.mock.calls[0][0]).toBe('/api/games/game/reviews/review/analyses');expect(screen.getByText('Current text and model version')).toBeTruthy();expect(screen.getByText('Earlier crash')).toBeTruthy();expect(screen.getByText('Provider unavailable')).toBeTruthy();expect(screen.getAllByText('Original text before the edit.')).toHaveLength(2);expect(screen.getAllByText(/Requested model: old-model/)).toHaveLength(2);
 await userEvent.click(screen.getByRole('button',{name:'Hide analysis history'}));expect(screen.queryByText('Historical version')).toBeNull();
 await userEvent.click(screen.getByRole('button',{name:'View analysis history'}));await screen.findByText('Historical version');expect(fetch).toHaveBeenCalledTimes(2);
});
it('shows errors and retries to a truthful empty state',async()=>{
 vi.stubGlobal('fetch',vi.fn().mockResolvedValueOnce(Response.json({message:'Backend unavailable'},{status:502})).mockResolvedValueOnce(Response.json([])));
 render(<AnalysisHistory gameId="game" reviewId="review" currentId={null}/>);await userEvent.click(screen.getByRole('button'));await screen.findByRole('alert');await userEvent.click(screen.getByRole('button',{name:'Retry history'}));await screen.findByText('No saved analyses for this review yet.');
});
it('aborts an in-flight history read when collapsed',async()=>{
 const fetch=vi.fn(()=>new Promise<Response>(()=>{}));vi.stubGlobal('fetch',fetch);
 render(<AnalysisHistory gameId="game" reviewId="review" currentId={null}/>);await userEvent.click(screen.getByRole('button'));await screen.findByRole('status');const signal=(fetch.mock.calls[0] as unknown as [string,RequestInit])[1].signal!;
 await userEvent.click(screen.getByRole('button',{name:'Hide analysis history'}));expect(signal.aborted).toBe(true);
});
