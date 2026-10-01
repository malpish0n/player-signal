'use client';
import {useEffect,useId,useState} from 'react';
import {api,message} from '@/lib/api';
import {AnalysisHistoryEntry,parseAnalysisHistory} from '@/lib/analysis-history';
import {AnalysisResult} from './analysis-result';

function HistoryEntries({gameId,reviewId,currentId}:{gameId:string;reviewId:string;currentId:string|null}){
 const [entries,setEntries]=useState<AnalysisHistoryEntry[]|null>(null),[error,setError]=useState(''),[retry,setRetry]=useState(0);
 useEffect(()=>{
  const controller=new AbortController();
  api(`games/${gameId}/reviews/${reviewId}/analyses`,parseAnalysisHistory,{signal:controller.signal})
   .then(value=>{if(!controller.signal.aborted){setEntries(value);setError('');}})
   .catch(error=>{if(!controller.signal.aborted)setError(message(error));});
  return()=>controller.abort();
 },[gameId,reviewId,retry]);
 return <div className="analysis-history-content">
  <p className="muted">Saved versions for this review, newest first. Source text below is the exact input at analysis time and may differ from the current review. Retrying a failed version updates that version; this is not a log of every attempt.</p>
  {error?<div role="alert"><p className="error">{error}</p><button onClick={()=>{setError('');setRetry(value=>value+1);}}>Retry history</button></div>:!entries?<p role="status">Loading analysis history…</p>:entries.length===0?<p className="muted">No saved analyses for this review yet.</p>:entries.map(entry=><article className="history-entry" key={entry.analysis.id}>
   <div className="review-meta"><strong>{entry.analysis.id===currentId?'Current text and model version':'Historical version'}</strong>Updated <time dateTime={entry.analysis.updatedAt}>{new Date(entry.analysis.updatedAt).toLocaleString(undefined,{timeZone:'UTC'})} UTC</time></div>
   <p className="muted">Provider: {entry.analysis.provider} · Requested model: {entry.analysis.model} · Prompt: {entry.analysis.promptVersion}</p>
   <AnalysisResult analysis={entry.analysis}/>
   <details><summary>Original analysis input · {entry.sourceLanguage}</summary><p className="review-text">{entry.sourceText||'(Empty input)'}</p><p className="muted history-hash">Input SHA-256: <code>{entry.inputHash}</code></p></details>
  </article>)}
 </div>;
}
export function AnalysisHistory({gameId,reviewId,currentId}:{gameId:string;reviewId:string;currentId:string|null}){
 const [open,setOpen]=useState(false);const panel=useId();
 return <div className="analysis-history"><button aria-expanded={open} aria-controls={panel} onClick={()=>setOpen(value=>!value)}>{open?'Hide analysis history':'View analysis history'}</button><div id={panel}>{open&&<HistoryEntries key={`${gameId}/${reviewId}/${currentId}`} gameId={gameId} reviewId={reviewId} currentId={currentId}/>}</div></div>;
}
