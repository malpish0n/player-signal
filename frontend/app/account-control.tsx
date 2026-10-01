'use client';
import Link from 'next/link';
import {useEffect,useState} from 'react';
import {api,message} from '@/lib/api';
import {Session,parseSession} from '@/lib/auth';
export function AccountControl(){const [session,setSession]=useState<Session|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 useEffect(()=>{const c=new AbortController();api('auth/session',parseSession,{signal:c.signal}).then(s=>{if(!c.signal.aborted)setSession(s);}).catch(e=>{if(!c.signal.aborted)setError(message(e));});return()=>c.abort();},[]);
 // A full navigation discards cached workspace data when the account changes.
 // eslint-disable-next-line @next/next/no-location-assign-relative-destination
 async function logout(){setBusy(true);try{await api('auth/logout',parseSession,{method:'POST'});window.location.assign('/login');}catch(e){setError(message(e));setBusy(false);}}
 return <div className="account-control">{session?.enabled?(session.user?<><span className="account-workspace" title={session.user.email}>{session.user.workspaceName}</span><button onClick={()=>void logout()} disabled={busy}>{busy?'Signing out…':'Sign out'}</button></>:<Link href="/login">Sign in</Link>):<Link href="/login">Account settings</Link>}{error&&<span role="alert" className="error">{error}</span>}</div>;
}
