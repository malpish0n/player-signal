'use client';
import Link from 'next/link';
import {useRef,useState} from 'react';
import {api,message} from '@/lib/api';
import {parseReport} from '@/lib/reports';
export function SaveComparisonReport({id,date,days}:{id:string;date:string;days:number}){
 const request=useRef<string|null>(null),[saved,setSaved]=useState<string|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState('');
 async function save(){setBusy(true);setError('');try{request.current??=crypto.randomUUID();const report=await api(`games/${id}/reports/generate`,parseReport,{method:'POST',body:JSON.stringify({type:'PATCH',date,days,requestId:request.current})});setSaved(report.report.id);}catch(e){setError(message(e));}finally{setBusy(false);}}
 return <section className="panel"><h2>Save this comparison</h2><p>Create a private report with preserved figures, summary and source excerpts. You can share that snapshot from Reports. Saving recalculates the comparison from current data.</p>{saved?<Link href={`/games/${id}/reports?report=${saved}`}>Open saved patch report →</Link>:<button disabled={busy} onClick={()=>void save()}>{busy?'Saving snapshot…':'Save patch report'}</button>}{error&&<p role="alert">{error}</p>}</section>;
}
