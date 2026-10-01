'use client';
import Link from 'next/link';
import {ComparisonReportExport} from './report-export';
import {useEffect,useState} from 'react';
import {api,message} from '@/lib/api';
import {Comparison,parseComparison,comparisonReviews} from '@/lib/comparison';
export function ComparisonWorkspace({id,date,days}:{id:string;date:string;days:string}){
 const [data,setData]=useState<Comparison|null>(null),[error,setError]=useState(''),[retry,setRetry]=useState(0);
 useEffect(()=>{if(!date)return;const c=new AbortController();api(`games/${id}/comparison?${new URLSearchParams({date,days})}`,parseComparison,{signal:c.signal}).then(value=>{if(!c.signal.aborted){setData(value);setError('');}}).catch(e=>{if(!c.signal.aborted)setError(message(e));});return()=>c.abort();},[id,date,days,retry]);
 function change(value:number|null,suffix:string){return value===null?'No comparison baseline':`${value>0?'+':''}${value.toFixed(1)}${suffix}`;}
 return <><div className="eyebrow">{data?.game.name??'GAME WORKSPACE'} · DATE COMPARISON</div><h1>Before & after</h1><p className="intro">Compare player recommendations around a date, such as an update release. This does not prove that the update caused a change.</p>
 <form className="panel filters" action={`/games/${id}/comparison`}><div><label htmlFor="comparison-date">Comparison date (UTC)</label><input id="comparison-date" name="date" type="date" required defaultValue={date} min="0001-01-01" max="9999-12-31"/></div><div><label htmlFor="comparison-days">Days on each side</label><select id="comparison-days" name="days" defaultValue={days}><option value="7">7 days</option><option value="30">30 days</option><option value="90">90 days</option></select></div><button type="submit">Compare periods</button></form>
 <p className="muted">The selected day starts the after period. Both windows must be complete UTC days; choose a date at least {days} days in the past.</p>
 {error&&<div className="error-banner" role="alert"><p>{error}</p><button onClick={()=>setRetry(value=>value+1)}>Retry comparison</button></div>}
 {!date?<section className="panel"><h2>Choose a date to compare</h2><p>Uses the reviews already imported for this game. No new import or AI analysis is started.</p></section>:!data&&!error?<p role="status">Comparing review periods…</p>:data&&<>
 <div className="data-notice">Imported sample only · {data.imported.toLocaleString()} reviews stored in total. Missing imports and small samples can distort comparisons.</div>
 <section className="panel"><h2>{data.game.name} · {data.days}-day windows</h2><div className="table-scroll"><table><thead><tr><th scope="col">Metric</th><th scope="col">Before</th><th scope="col">After</th></tr></thead><tbody>
 <tr><th scope="row">Dates (UTC, inclusive)</th><td>{data.before.from} – {data.before.to}</td><td>{data.after.from} – {data.after.to}</td></tr>
 <tr><th scope="row">Imported reviews</th><td>{data.before.reviews.toLocaleString()}</td><td>{data.after.reviews.toLocaleString()}</td></tr>
 <tr><th scope="row">Recommended</th><td>{data.before.recommended}</td><td>{data.after.recommended}</td></tr>
 <tr><th scope="row">Recommendation rate</th>{[data.before,data.after].map((w,i)=><td key={i}>{w.recommendationRate===null?'No reviews':`${(w.recommendationRate*100).toFixed(1)}%`}</td>)}</tr>
 <tr><th scope="row">Source evidence</th><td><Link href={comparisonReviews(id,data.before)}>Review before period →</Link></td><td><Link href={comparisonReviews(id,data.after)}>Review after period →</Link></td></tr>
 </tbody></table></div><p>Review volume: <strong>{change(data.reviewChangePercent,'%')}</strong> · Recommendation rate: <strong>{change(data.recommendationChangePoints,' pp')}</strong></p><p className="muted">Recommendations are Steam votes, not AI sentiment. Changes compare after with before; pp means percentage points. Calculated {new Date(data.calculatedAt).toLocaleString(undefined,{timeZone:'UTC'})} UTC.</p></section>
 <ComparisonReportExport data={data}/></>}
 </>;
}
