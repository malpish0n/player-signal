import {expect,it} from 'vitest';
import {workspaceDestination,accountLink} from './account-navigation';
it('preserves known workspace paths and filters',()=>{
 const path='/games/53d0054e-03ac-433e-8cdb-3ff28d640140/issues?minSeverity=75';expect(workspaceDestination(path)).toBe(path);
 expect(new URL(accountLink('register',path),'https://playersignal.invalid').searchParams.get('next')).toBe(path);
});
it.each([null,'https://evil.test','//evil.test','/\\evil.test','/login?next=/login','/api/auth/logout','javascript:alert(1)','/games/invalid','/%2f%2fevil.test','/\nevil.test','/#evil'])('rejects unsafe or non-workspace destination %s',value=>{expect(workspaceDestination(value)).toBe('/');});
