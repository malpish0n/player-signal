'use client';
import Link from 'next/link';
import {FormEvent,useEffect,useState} from 'react';
import {api,message} from '@/lib/api';
import {GameUpdate,parseUpdate,parseUpdates} from '@/lib/updates';
export function UpdatesWorkspace({id}:{id:string}){
 const [items,setItems]=useState<GameUpdate[]|null>(null),[error,setError]=useState(''),[reload,setReload]=useState(0);
 const [editing,setEditing]=useState<GameUpdate|null>(null),[title,setTitle]=useState(''),[date,setDate]=useState(''),[busy,setBusy]=useState(false),[notice,setNotice]=useState('');
 useEffect(()=>{const c=new AbortController();api(`games/${id}/updates`,parseUpdates,{signal:c.signal}).then(v=>{if(!c.signal.aborted)setItems(v);}).catch(e=>{if(!c.signal.aborted)setError(message(e));});return()=>c.abort();},[id,reload]);
 function reset(){setEditing(null);setTitle('');setDate('');}
 async function save(e:FormEvent<HTMLFormElement>){e.preventDefault();setBusy(true);setError('');setNotice('');try{
  const saved=await api(`games/${id}/updates${editing?`/${editing.id}`:''}`,parseUpdate,{method:'POST',body:JSON.stringify({title,releasedOn:date,version:editing?.version})});
  setItems(previous=>[...(previous??[]).filter(v=>v.id!==saved.id),saved].sort((a,b)=>b.releasedOn.localeCompare(a.releasedOn)||a.id.localeCompare(b.id)));reset();setNotice('Update saved.');
 }catch(e){setError(message(e));}finally{setBusy(false);}}
 return <><div className="eyebrow">GAME WORKSPACE · RELEASE DATES</div><h1>Updates</h1><p className="intro">Save release dates and compare player feedback around each update. Dates are entered manually and use UTC.</p>
 {error&&<div className="error-banner" role="alert"><p>{error}</p><button disabled={busy} onClick={()=>{reset();setError('');setNotice('');setItems(null);setReload(v=>v+1);}}>Reload updates</button></div>}
 {notice&&<p role="status">{notice}</p>}
 {items===null&&!error?<p role="status">Loading updates…</p>:items!==null&&<>
 <section className="panel"><h2>{editing?'Edit update':'Add update'}</h2><form className="filters" onSubmit={save}><div><label htmlFor="update-title">Update name</label><input id="update-title" value={title} onChange={e=>setTitle(e.target.value)} required maxLength={120} disabled={busy}/></div><div><label htmlFor="update-date">Release date (UTC)</label><input id="update-date" type="date" min="0001-01-01" max="9999-12-31" value={date} onChange={e=>setDate(e.target.value)} required disabled={busy}/></div><button disabled={busy||(!editing&&items.length>=100)}>{busy?'Saving…':editing?'Save changes':'Add update'}</button>{editing&&<button type="button" disabled={busy} onClick={reset}>Cancel editing</button>}</form><p className="muted">Up to 100 updates per game. Planned dates can be saved; comparisons require complete periods after release.</p></section>
 <section className="panel"><h2>Saved updates · {items.length}</h2>{items.length===0?<p>No updates saved yet. Add a release date above.</p>:<div className="table-scroll issue-table"><table><thead><tr><th scope="col">Update</th><th scope="col">Release (UTC)</th><th scope="col">Compare before & after</th><th scope="col">Actions</th></tr></thead><tbody>{items.map(item=><tr key={item.id}><td style={{whiteSpace:'normal',overflowWrap:'anywhere'}}>{item.title}</td><td>{item.releasedOn}</td><td>{[7,30,90].map(days=><span key={days}><Link href={`/games/${id}/comparison?${new URLSearchParams({date:item.releasedOn,days:String(days)})}`}>{days} days</Link>{' '}</span>)}</td><td><button disabled={busy} onClick={()=>{setEditing(item);setTitle(item.title);setDate(item.releasedOn);setNotice('');}}>Edit <span className="sr-only">{item.title}</span></button></td></tr>)}</tbody></table></div>}<p className="muted">Comparisons use the currently imported sample and do not establish that a release caused a change. Editing a release date changes these shortcuts, not previously downloaded reports.</p></section>
 </>}
 </>;
}
