'use client';
import {useState} from 'react';
import {message} from '@/lib/api';
import {SESSION_REQUIRED_EVENT} from '@/lib/account-navigation';
export function ReviewExport({id, language, vote, q}: {id:string; language:string; vote:string; q:string}) {
  const [busy,setBusy]=useState(false), [error,setError]=useState('');
  async function download() {
    setBusy(true);setError('');
    try {
      const query=new URLSearchParams();
      if(language) query.set('language',language);
      if(vote) query.set('votedUp',vote);
      if(q) query.set('q',q);
      const response=await fetch(`/api/games/${id}/reviews/export?${query}`,{cache:'no-store'});
      if(response.status===401) window.dispatchEvent(new Event(SESSION_REQUIRED_EVENT));
      if(!response.ok) {
        const value=await response.json();
        throw new Error(typeof value.message==='string'?value.message:'Export failed. Please retry.');
      }
      if(!response.headers.get('Content-Type')?.startsWith('text/csv')) throw new Error('Unexpected export response. Please retry.');
      const url=URL.createObjectURL(await response.blob());
      const link=document.createElement('a');link.href=url;link.download=`playersignal-${id}-reviews.csv`;
      document.body.appendChild(link);link.click();link.remove();
      // Allow the browser to consume the download before releasing the object URL.
      setTimeout(()=>URL.revokeObjectURL(url),1000);
    } catch(error) {setError(message(error));}
    finally {setBusy(false);}
  }
  return <div className="review-export"><button disabled={busy} onClick={()=>void download()}>{busy?'Preparing CSV…':'Export filtered CSV'}</button><p className="muted">All matching imported reviews, across pages. Up to 5,000 rows / 10 MiB. The imported sample may be incomplete.</p>{error&&<p className="error" role="alert">{error}</p>}</div>;
}
