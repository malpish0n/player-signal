'use client';
import {useEffect,useState} from 'react';
import {api,message} from '@/lib/api';
type Usage={month:string;analysisAttempts:number;monthlyLimit:number;inputTokens:number;outputTokens:number;unknownAttempts:number};
function parse(value:unknown):Usage{if(!value||typeof value!=='object')throw Error('Invalid usage response.');const d=value as Record<string,unknown>;if(typeof d.month!=='string')throw Error('Invalid usage month.');for(const key of ['analysisAttempts','monthlyLimit','inputTokens','outputTokens','unknownAttempts'])if(typeof d[key]!=='number'||!Number.isSafeInteger(d[key])||(d[key] as number)<0)throw Error('Invalid usage count.');return d as Usage;}
export function UsagePanel({id}:{id:string}){
 const [value,setValue]=useState<Usage|null>(null),[error,setError]=useState(''),[retry,setRetry]=useState(0);
 useEffect(()=>{const c=new AbortController();api(`games/${id}/usage`,parse,{signal:c.signal}).then(v=>{if(!c.signal.aborted){setValue(v);setError('');}}).catch(e=>{if(!c.signal.aborted)setError(message(e));});return()=>c.abort();},[id,retry]);
 return <section className="panel compact"><h2>Workspace usage</h2>{error?<p role="alert">{error}</p>:value?<><p>{value.analysisAttempts} / {value.monthlyLimit} analysis attempts · UTC month starting {value.month}</p><p className="muted">{value.inputTokens} input tokens · {value.outputTokens} output tokens reported. {value.unknownAttempts} attempts have unconfirmed token usage. Retries consume quota; cached/skipped reviews do not. These counters are not an invoice or a monetary cost estimate.</p></>:<p role="status">Loading usage…</p>}<button onClick={()=>setRetry(v=>v+1)}>Refresh usage</button></section>;
}
