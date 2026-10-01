'use client';
import Link from 'next/link';
import {useEffect,useState} from 'react';
import {api,message} from '@/lib/api';
type View={id:string;name:string;path:string};
function view(v:unknown):View{if(!v||typeof v!=='object')throw Error('Invalid saved view.');const d=v as Record<string,unknown>;if(typeof d.id!=='string'||typeof d.name!=='string'||typeof d.path!=='string'||!/^\/games\/[0-9a-f-]{36}(?:\/issues)?(?:\?[^#]*)?$/.test(d.path))throw Error('Invalid saved view.');return d as View;}
export function SavedViews({id}:{id:string}){
 const [items,setItems]=useState<View[]|null>(null),[name,setName]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[retry,setRetry]=useState(0);
 useEffect(()=>{const c=new AbortController();api(`games/${id}/saved-views`,v=>{if(!Array.isArray(v))throw Error('Invalid saved views.');return v.map(view);},{signal:c.signal}).then(v=>{if(!c.signal.aborted){setItems(v);setError('');}}).catch(e=>{if(!c.signal.aborted)setError(message(e));});return()=>c.abort();},[id,retry]);
 async function save(){setBusy(true);setError('');try{const saved=await api(`games/${id}/saved-views`,view,{method:'POST',body:JSON.stringify({name,path:window.location.pathname+window.location.search})});setItems(v=>[saved,...(v??[])]);setName('');}catch(e){setError(message(e));}finally{setBusy(false);}}
 async function remove(key:string){setBusy(true);setError('');try{await api(`games/${id}/saved-views/${key}/remove`,v=>v,{method:'POST'});setItems(v=>v?.filter(i=>i.id!==key)??null);}catch(e){setError(message(e));}finally{setBusy(false);}}
 return <details className="panel"><summary>Saved views {items?`(${items.length}/20)`:''}</summary><p className="muted">Save the currently applied URL filters for this game. These bookmarks are shared within the workspace.</p>{error&&<p role="alert">{error}</p>}<form className="filters" onSubmit={e=>{e.preventDefault();void save();}}><div><label htmlFor="saved-view-name">View name</label><input id="saved-view-name" maxLength={80} required value={name} onChange={e=>setName(e.target.value)}/></div><button disabled={busy||!items||items.length>=20}>Save current view</button></form><ul>{items?.map(v=><li key={v.id}><Link href={v.path}>{v.name}</Link> <button disabled={busy} onClick={()=>void remove(v.id)}>Remove <span className="sr-only">{v.name}</span></button></li>)}</ul><button disabled={busy} onClick={()=>setRetry(v=>v+1)}>Reload saved views</button></details>;
}
