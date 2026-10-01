// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {render,screen,cleanup} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {AppShell} from './app-shell';
vi.mock('./workspace-access',()=>({WorkspaceAccess:({children}:{children:React.ReactNode})=>children}));
vi.mock('./account-control',()=>({AccountControl:()=>null}));
vi.mock('next/navigation',()=>({usePathname:()=>'/games/test/overview'}));
afterEach(cleanup);
it('marks the selected page and opens/closes mobile navigation',async()=>{render(<AppShell>Page</AppShell>);expect(screen.getByRole('link',{name:'Overview'}).getAttribute('aria-current')).toBe('page');await userEvent.click(screen.getByRole('button',{name:'Open navigation'}));expect(screen.getByRole('button',{name:'Close navigation'}).getAttribute('aria-expanded')).toBe('true');await userEvent.click(screen.getByRole('link',{name:'Issues'}));expect(screen.getByRole('button',{name:'Open navigation'}).getAttribute('aria-expanded')).toBe('false');});
