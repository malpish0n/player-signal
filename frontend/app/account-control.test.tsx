// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {act,cleanup,render,screen} from '@testing-library/react';
import {AccountControl} from './account-control';
import {SESSION_REQUIRED_EVENT} from '@/lib/account-navigation';
const anonymous={enabled:true,user:null,csrfToken:'token'};
const signedIn={...anonymous,user:{id:'u',email:'a@example.test',workspaceId:'w',workspaceName:'Private workspace'}};
afterEach(()=>{cleanup();vi.unstubAllGlobals();window.history.replaceState(null,'','/');});
it('preserves a return link in the header and removes stale account labels on focus',async()=>{
 window.history.replaceState(null,'','/games/53d0054e-03ac-433e-8cdb-3ff28d640140/overview?days=7');
 vi.stubGlobal('fetch',vi.fn().mockResolvedValueOnce(Response.json(signedIn)).mockResolvedValueOnce(Response.json(anonymous)));
 render(<AccountControl/>);await screen.findByText('Private workspace');
 await act(async()=>window.dispatchEvent(new Event('focus')));
 const link=await screen.findByRole('link',{name:'Sign in'});
 expect(screen.queryByText('Private workspace')).toBeNull();
 expect(new URL(link.getAttribute('href')!,'http://localhost').searchParams.get('next')).toBe(window.location.pathname+window.location.search);
});
it('does not restore an expired account from an older request',async()=>{
 let finish!:(value:Response)=>void;vi.stubGlobal('fetch',vi.fn(()=>new Promise<Response>(resolve=>{finish=resolve;})));
 render(<AccountControl/>);act(()=>window.dispatchEvent(new Event(SESSION_REQUIRED_EVENT)));
 await act(async()=>finish(Response.json(signedIn)));expect(screen.queryByText('Private workspace')).toBeNull();expect(screen.getByRole('link',{name:'Sign in'})).toBeTruthy();
});
