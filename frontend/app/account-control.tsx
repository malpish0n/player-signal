'use client';
import Link from 'next/link';
import {useEffect, useState} from 'react';
import {api, message} from '@/lib/api';
import {Session, parseSession} from '@/lib/auth';
import {accountLink, SESSION_REQUIRED_EVENT} from '@/lib/account-navigation';

export function AccountControl() {
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let controller: AbortController | undefined;
    async function check() {
      controller?.abort();
      const current = new AbortController();
      controller = current;
      try {
        const value = await api('auth/session', parseSession, {signal: current.signal});
        if (!current.signal.aborted) { setSession(value); setError(''); }
      } catch (error) {
        if (!current.signal.aborted) { setSession(null); setError(message(error)); }
      }
    }
    function expired() {
      controller?.abort();
      setSession({enabled: true, user: null, csrfToken: null});
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
  }, []);
  async function logout() {
    setBusy(true);
    try {
      await api('auth/logout', parseSession, {method: 'POST'});
      // A full navigation discards cached workspace data when the account changes.
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination
      window.location.assign('/login');
    } catch (error) { setError(message(error)); setBusy(false); }
  }
  const destination = typeof window === 'undefined' ? '/' :
    new URLSearchParams(window.location.search).get('next') || window.location.pathname + window.location.search;
  return <div className="account-control">
    {session?.enabled ? session.user ? <>
      <span className="account-workspace" title={session.user.email}>{session.user.workspaceName}</span>
      <button onClick={() => void logout()} disabled={busy}>{busy ? 'Signing out…' : 'Sign out'}</button>
    </> : <Link href={accountLink('login', destination)}>Sign in</Link> : <Link href="/login">Account settings</Link>}
    {error && <span role="alert" className="error">{error}</span>}
  </div>;
}
