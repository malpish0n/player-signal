'use client';
import Link from 'next/link';
import {useEffect,useState} from 'react';
import {api,message} from '@/lib/api';
import {parseSession,Session} from '@/lib/auth';
import {Button} from '@/components/ui/button';
export function AccountForm({register=false}:{register?:boolean}){
 const [session,setSession]=useState<Session|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[retry,setRetry]=useState(0);
 useEffect(()=>{const c=new AbortController();api('auth/session',parseSession,{signal:c.signal}).then(s=>{if(!c.signal.aborted){setSession(s);setError('');}}).catch(e=>{if(!c.signal.aborted)setError(message(e));});return()=>c.abort();},[retry]);
 // A full navigation discards cached workspace data when the account changes.
 // eslint-disable-next-line @next/next/no-location-assign-relative-destination
 async function submit(e:React.FormEvent<HTMLFormElement>){e.preventDefault();const data=new FormData(e.currentTarget);setBusy(true);setError('');try{await api(`auth/${register?'register':'login'}`,parseSession,{method:'POST',body:JSON.stringify({email:data.get('email'),password:data.get('password'),workspaceName:data.get('workspaceName')})});window.location.assign('/');}catch(e){setError(message(e));setBusy(false);}}
 return <div className="account-page"><div className="eyebrow">PLAYERSIGNAL ACCOUNT</div><h1>{register?'Create your workspace':'Sign in to your workspace'}</h1><p className="muted">{register?'Your connected games and analyses belong to your workspace.':'Use your email and password to access your games.'}</p>
 {error&&<div className="error-banner" role="alert"><p>{error}</p>{!session&&<Button variant="secondary" onClick={()=>setRetry(v=>v+1)}>Retry loading</Button>}</div>}
 {!session&&!error&&<p role="status">Checking account configuration…</p>}
 {session&&!session.enabled?<section className="panel"><h2>Local mode is active</h2><p>Your current games remain available without signing in.</p><p className="muted">To enable isolated accounts, set AUTH_ENABLED=true on the backend. Existing local data stays in its local workspace and is not claimed by new registrations.</p><Link href="/">Return to your games →</Link></section>:session?.user?<section className="panel"><h2>{session.user.workspaceName}</h2><p>Signed in as {session.user.email}</p><Link href="/">Open your games →</Link></section>:session&&<section className="panel"><form onSubmit={submit}>
 <label htmlFor="email">Email</label><input id="email" name="email" type="email" autoComplete="username" maxLength={254} required/>
 {register&&<><label htmlFor="workspaceName">Workspace name</label><input id="workspaceName" name="workspaceName" maxLength={80} required autoComplete="organization"/></>}
 <label htmlFor="password">Password</label><input id="password" name="password" type="password" autoComplete={register?'new-password':'current-password'} minLength={register?12:undefined} maxLength={72} required aria-describedby={register?'password-help':undefined}/>
 {register&&<p id="password-help" className="muted">Use at least 12 characters; maximum 72 UTF-8 bytes. A long, unique passphrase works well.</p>}
 <Button type="submit" disabled={busy}>{busy?'Please wait…':register?'Create account':'Sign in'}</Button></form><p className="muted">{register?'Already have an account?':'New to PlayerSignal?'} <Link href={register?'/login':'/register'}>{register?'Sign in':'Create an account'}</Link></p></section>}
 </div>;
}
