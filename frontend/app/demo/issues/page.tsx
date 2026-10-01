'use client';
import Link from 'next/link';
import {useEffect,useState} from 'react';
import {api,message} from '@/lib/api';
import {parseIssueDemo} from '@/lib/issues';
import {IssueMetrics,IssueEvidence} from '@/app/issue-components';
export default function Page(){const [data,setData]=useState<ReturnType<typeof parseIssueDemo>|null>(null),[error,setError]=useState(''),[retry,setRetry]=useState(0);
useEffect(()=>{const c=new AbortController();api('issues/demo',parseIssueDemo,{signal:c.signal}).then(d=>{if(!c.signal.aborted){setData(d);setError('');}}).catch(e=>{if(!c.signal.aborted)setError(message(e));});return()=>c.abort();},[retry]);
return <><Link className="back-link" href="/demo">← Classification demo</Link><div className="eyebrow">SYNTHETIC DEMO</div><h1>Recurring problems, with evidence</h1><p className="warning">Six invented reviews, grouped by the same local algorithm used for live analyses. No API calls or database writes.</p>{error&&<div role="alert"><p className="error">{error}</p><button onClick={()=>setRetry(v=>v+1)}>Retry demo</button></div>}{!data&&!error&&<p role="status">Loading issue demo…</p>}{data&&<><p className="muted">Fixed reference time: {new Date(data.asOf).toLocaleString()} · {data.algorithm}. Each issue has one mention in each 7-day window. Confidence is illustrative; severity is the mean of LOW=25, MEDIUM=50, HIGH=75, CRITICAL=100.</p>{data.issues.map(({issue,evidence})=><section className="panel" key={issue.id}><div className="eyebrow">{issue.category} · {issue.status}</div><h2>{issue.title}</h2><IssueMetrics metrics={issue.metrics}/><details><summary>Inspect {evidence.length} supporting reviews</summary><IssueEvidence evidence={evidence}/></details></section>)}</>}</>;}
