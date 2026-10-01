import {afterEach,expect,it,vi} from 'vitest';
import {api} from './api';
import {parseSession} from './auth';
afterEach(()=>vi.unstubAllGlobals());
it('gets a session-bound CSRF token before a write',async()=>{const fetch=vi.fn().mockResolvedValueOnce(Response.json({enabled:true,user:null,csrfToken:'test-token'})).mockResolvedValueOnce(Response.json({enabled:true,user:null,csrfToken:null}));vi.stubGlobal('fetch',fetch);await api('auth/logout',parseSession,{method:'POST'});expect(fetch.mock.calls[0][0]).toBe('/api/auth/session');expect(new Headers(fetch.mock.calls[1][1].headers).get('X-CSRF-TOKEN')).toBe('test-token');});
it('does not write when session verification fails',async()=>{const fetch=vi.fn().mockResolvedValue(Response.json({}, {status:503}));vi.stubGlobal('fetch',fetch);await expect(api('auth/logout',parseSession,{method:'POST'})).rejects.toThrow('verify your session');expect(fetch).toHaveBeenCalledTimes(1);});
it('rejects incomplete account identity',()=>{expect(()=>parseSession({enabled:true,user:{email:'test@example.test'},csrfToken:'token'})).toThrow();});
