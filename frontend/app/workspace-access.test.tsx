// @vitest-environment jsdom
import {afterEach, expect, it, vi} from 'vitest';
import {act, cleanup, render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {useEffect} from 'react';
import {WorkspaceAccess} from './workspace-access';
import {api} from '@/lib/api';
import {SESSION_REQUIRED_EVENT} from '@/lib/account-navigation';
const anonymous = {enabled:true,user:null,csrfToken:'token'};
const signedIn = {...anonymous,user:{id:'user',email:'test@example.test',workspaceId:'workspace',workspaceName:'Test workspace'}};
afterEach(()=>{cleanup();vi.unstubAllGlobals();window.history.replaceState(null,'','/');});
it('never mounts protected content before verification or for anonymous users; preserves deep links',async()=>{
 window.history.replaceState(null,'','/games/53d0054e-03ac-433e-8cdb-3ff28d640140/issues?minSeverity=75');
 let finish!:(value:Response)=>void;
 vi.stubGlobal('fetch',vi.fn(()=>new Promise<Response>(resolve=>{finish=resolve;})));
 const mounted=vi.fn();function Private(){useEffect(mounted,[]);return <div>Private data</div>;}
 render(<WorkspaceAccess><Private/></WorkspaceAccess>);
 expect(screen.getByRole('status').textContent).toContain('Checking');expect(mounted).not.toHaveBeenCalled();
 await act(async()=>finish(Response.json(anonymous)));
 expect(screen.queryByText('Private data')).toBeNull();expect(mounted).not.toHaveBeenCalled();
 const link=screen.getByRole('link',{name:'Sign in'}).getAttribute('href')!;
 expect(new URL(link,'http://localhost').searchParams.get('next')).toBe(window.location.pathname+window.location.search);
});
it.each([{enabled:false,user:null,csrfToken:null},signedIn])('allows a verified local or signed-in workspace',async session=>{
 vi.stubGlobal('fetch',vi.fn().mockResolvedValue(Response.json(session)));render(<WorkspaceAccess>Private data</WorkspaceAccess>);await screen.findByText('Private data');
});
it('removes private data when a game API responds 401',async()=>{
 vi.stubGlobal('fetch',vi.fn().mockResolvedValueOnce(Response.json(signedIn)).mockResolvedValueOnce(Response.json({message:'Sign in.'},{status:401})));
 render(<WorkspaceAccess>Private data</WorkspaceAccess>);await screen.findByText('Private data');
 await act(async()=>{await expect(api('games',v=>v)).rejects.toThrow('Sign in.');});
 expect(screen.queryByText('Private data')).toBeNull();expect(screen.getByText('Sign in to continue')).toBeTruthy();
});
it('rechecks on focus and removes data for a changed session',async()=>{
 vi.stubGlobal('fetch',vi.fn().mockResolvedValueOnce(Response.json(signedIn)).mockResolvedValueOnce(Response.json(anonymous)));
 render(<WorkspaceAccess>Private data</WorkspaceAccess>);await screen.findByText('Private data');
 await act(async()=>window.dispatchEvent(new Event('focus')));await screen.findByText('Sign in to continue');expect(screen.queryByText('Private data')).toBeNull();
});
it('fails closed on session outage and supports retry',async()=>{
 vi.stubGlobal('fetch',vi.fn().mockRejectedValueOnce(Error('Backend unavailable')).mockResolvedValueOnce(Response.json(signedIn)));
 render(<WorkspaceAccess>Private data</WorkspaceAccess>);await screen.findByRole('alert');expect(screen.queryByText('Private data')).toBeNull();
 await userEvent.click(screen.getByRole('button',{name:'Retry access check'}));await screen.findByText('Private data');
});
it('ignores late checks after session expiry',async()=>{
 let finish!:(value:Response)=>void;vi.stubGlobal('fetch',vi.fn(()=>new Promise<Response>(resolve=>{finish=resolve;})));
 render(<WorkspaceAccess>Private data</WorkspaceAccess>);act(()=>window.dispatchEvent(new Event(SESSION_REQUIRED_EVENT)));
 await act(async()=>finish(Response.json(signedIn)));expect(screen.queryByText('Private data')).toBeNull();expect(screen.getByText('Sign in to continue')).toBeTruthy();
});
it('does not signal session expiry for invalid login credentials',async()=>{
 const expired=vi.fn();window.addEventListener(SESSION_REQUIRED_EVENT,expired);
 vi.stubGlobal('fetch',vi.fn().mockResolvedValue(Response.json({message:'Invalid credentials'},{status:401})));
 await expect(api('auth/login',v=>v)).rejects.toThrow('Invalid credentials');expect(expired).not.toHaveBeenCalled();window.removeEventListener(SESSION_REQUIRED_EVENT,expired);
});
