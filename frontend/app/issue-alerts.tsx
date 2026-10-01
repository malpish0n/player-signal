'use client';
import {useEffect,useState} from 'react';
import {api,message} from '@/lib/api';
import {record,string} from '@/lib/workspace';
type Policy={enabled:boolean;lastCheckedAt:string|null};
function parse(value:unknown):Policy{const v=record(value);if(typeof v.enabled!=='boolean')throw Error('Invalid alert policy.');return {enabled:v.enabled,lastCheckedAt:v.lastCheckedAt===null?null:string(v.lastCheckedAt)};}
export function IssueAlerts({id}:{id:string}){
 const [policy,setPolicy]=useState<Policy|null>(null),[error,setError]=useState(''),[notice,setNotice]=useState(''),[busy,setBusy]=useState(false),[retry,setRetry]=useState(0);
 useEffect(()=>{const c=new AbortController();api(`games/${id}/alerts`,parse,{signal:c.signal}).then(v=>{if(!c.signal.aborted){setPolicy(v);setError('');}}).catch(e=>{if(!c.signal.aborted)setError(message(e));});return()=>c.abort();},[id,retry]);
 async function save(){if(!policy)return;setBusy(true);setError('');try{setPolicy(await api(`games/${id}/alerts`,parse,{method:'POST',body:JSON.stringify({enabled:!policy.enabled})}));setNotice('Alert preference saved.');}catch(e){setError(message(e));}finally{setBusy(false);}}
 async function check(){setBusy(true);setError('');try{const result=await api(`games/${id}/alerts/check`,v=>{const d=record(v);return `${d.created} new notifications. ${string(d.reason)}`;},{method:'POST'});setNotice(result);setRetry(v=>v+1);}catch(e){setError(message(e));}finally{setBusy(false);}}
 return <section className="panel"><h2>Growing issue alerts</h2><p>In-app alerts compare two complete UTC weeks. They require 20 analyzed reviews per week, at least 3 current mentions, a rise of 5 percentage points and average severity of at least 75/100. Resolved and ignored issues are excluded.</p><p className="muted">Disabled by default. Checked hourly when the server scheduler is enabled; missing or stale grouping is skipped. These are investigation signals, not confirmed regressions. No email is sent.</p>{policy&&<><p>Status: {policy.enabled?'enabled':'disabled'} · last check: {policy.lastCheckedAt??'never'}</p><button disabled={busy} onClick={()=>void save()}>{policy.enabled?'Disable alerts':'Enable alerts'}</button><button disabled={busy||!policy.enabled} onClick={()=>void check()}>Check now</button></>}{notice&&<p role="status">{notice} Refresh notifications above to see new alerts.</p>}{error&&<p role="alert">{error}</p>}<button onClick={()=>setRetry(v=>v+1)}>Refresh alert settings</button></section>;
}
