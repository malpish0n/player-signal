'use client';
import {useCallback,useEffect,useState} from 'react';
import {api,message,parseRun} from '@/lib/api';
import {Overview,parseOverview} from '@/lib/overview';
import {Button} from '@/components/ui/button';
import {OverviewDashboard,OverviewSkeleton} from './overview-dashboard';
export function OverviewWorkspace({id,days='30',demo=false}:{id?:string;days?:string;demo?:boolean}){
 const [data,setData]=useState<Overview|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[retry,setRetry]=useState(0);
 const load=useCallback(async(signal:AbortSignal)=>{try{const result=await api(`${demo?'overview/demo':`games/${id}/overview`}?days=${encodeURIComponent(days)}`,parseOverview,{signal});if(!signal.aborted){setData(result);setError('');}}catch(e){if(!signal.aborted)setError(message(e));}},[id,days,demo]);
 // load only changes state after an awaited request.
 // eslint-disable-next-line react-hooks/set-state-in-effect
 useEffect(()=>{const c=new AbortController();void load(c.signal);return()=>c.abort();},[load,retry]);
 useEffect(()=>{if(data?.ingestion?.status!=='RUNNING'&&data?.analysis.latestRun?.status!=='RUNNING')return;const c=new AbortController();const timer=setTimeout(()=>void load(c.signal),2000);return()=>{clearTimeout(timer);c.abort();};},[data,load]);
 async function sync(){setBusy(true);setError('');try{const ingestion=await api(`games/${id}/sync`,parseRun,{method:'POST'});setData(d=>d?{...d,ingestion}:d);setRetry(v=>v+1);}catch(e){setError(message(e));}finally{setBusy(false);}}
 return <>{error&&<section className="error-banner" role="alert"><strong>Could not refresh overview</strong><p>{error}</p>{data&&<p>Displayed data may be stale. Last successful refresh: {new Date(data.asOf).toLocaleString()}.</p>}<Button variant="secondary" onClick={()=>setRetry(v=>v+1)}>Retry loading</Button></section>}{data?<OverviewDashboard data={data} onSync={()=>void sync()} busy={busy}/>:!error?<OverviewSkeleton/>:null}</>;
}
