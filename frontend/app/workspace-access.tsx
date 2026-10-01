'use client';
import Link from 'next/link';
import {useEffect, useState} from 'react';
import {api, message} from '@/lib/api';
import {parseSession, Session} from '@/lib/auth';
import {accountLink, SESSION_REQUIRED_EVENT} from '@/lib/account-navigation';
import {Button} from '@/components/ui/button';

type Access = {status: 'checking'} | {status: 'ready'; session: Session} | {status: 'error'; message: string};

export function WorkspaceAccess({children}: {children: React.ReactNode}) {
  const [access, setAccess] = useState<Access>({status: 'checking'});
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let controller: AbortController | undefined;
    async function check() {
      controller?.abort();
      const current = new AbortController();
      controller = current;
      setAccess({status: 'checking'});
      try {
        const session = await api('auth/session', parseSession, {signal: current.signal});
        if (!current.signal.aborted) setAccess({status: 'ready', session});
      } catch (error) {
        if (!current.signal.aborted) setAccess({status: 'error', message: message(error)});
      }
    }
    function expired() {
      controller?.abort();
      setAccess({status: 'ready', session: {enabled: true, user: null, csrfToken: null}});
    }
    function visible() { if (document.visibilityState === 'visible') void check(); }
    void check();
    window.addEventListener(SESSION_REQUIRED_EVENT, expired);
    window.addEventListener('focus', check);
    document.addEventListener('visibilitychange', visible);
    return () => {
      controller?.abort();
      window.removeEventListener(SESSION_REQUIRED_EVENT, expired);
      window.removeEventListener('focus', check);
      document.removeEventListener('visibilitychange', visible);
    };
  }, [attempt]);

  if (access.status === 'checking') return <p role="status">Checking workspace access…</p>;
  if (access.status === 'error') return <section className="panel" role="alert"><h1>Cannot verify workspace access</h1><p>{access.message}</p><Button onClick={() => setAttempt(value => value + 1)}>Retry access check</Button></section>;
  if (!access.session.enabled || access.session.user) return children;
  const destination = window.location.pathname + window.location.search;
  return <section className="panel account-page"><div className="eyebrow">YOUR WORKSPACE</div><h1>Sign in to continue</h1><p>Your session may have expired. Sign in to open this workspace page. Your imported reviews are preserved.</p><div className="access-actions"><Link className="button" href={accountLink('login', destination)}>Sign in</Link><Link className="button" href={accountLink('register', destination)}>Create an account</Link></div><p className="muted"><Link href="/demo/overview">Explore the demo without an account →</Link></p></section>;
}
