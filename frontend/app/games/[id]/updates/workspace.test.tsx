// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {UpdatesWorkspace} from './workspace';
const saved={id:'update',title:'Patch 1',releasedOn:'2026-09-20',version:0,createdAt:'2026-10-01T00:00:00Z',updatedAt:'2026-10-01T00:00:00Z'};
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('creates an update and offers exact comparison windows',async()=>{
 const fetch=vi.fn().mockResolvedValueOnce(Response.json([])).mockResolvedValueOnce(Response.json({enabled:false})).mockResolvedValueOnce(Response.json(saved));vi.stubGlobal('fetch',fetch);
 render(<UpdatesWorkspace id="game"/>);await screen.findByText('No updates saved yet. Add a release date above.');
 await userEvent.type(screen.getByLabelText('Update name'),'Patch 1');await userEvent.type(screen.getByLabelText('Release date (UTC)'),'2026-09-20');await userEvent.click(screen.getByRole('button',{name:'Add update'}));
 await screen.findByText('Update saved.');expect(JSON.parse(fetch.mock.calls[2][1].body)).toEqual({title:'Patch 1',releasedOn:'2026-09-20'});expect(screen.getByRole('link',{name:'30 days'}).getAttribute('href')).toBe('/games/game/comparison?date=2026-09-20&days=30');
});
it('sends the edit version and keeps unsaved input after a conflict',async()=>{
 const fetch=vi.fn().mockResolvedValueOnce(Response.json([saved])).mockResolvedValueOnce(Response.json({enabled:false})).mockResolvedValueOnce(Response.json({message:'Reload before editing.'},{status:409}));vi.stubGlobal('fetch',fetch);
 render(<UpdatesWorkspace id="game"/>);await userEvent.click(await screen.findByRole('button',{name:'Edit Patch 1'}));await userEvent.type(screen.getByLabelText('Update name'),' revised');await userEvent.click(screen.getByRole('button',{name:'Save changes'}));
 expect((await screen.findByRole('alert')).textContent).toContain('Reload before editing.');expect(JSON.parse(fetch.mock.calls[2][1].body).version).toBe(0);expect((screen.getByLabelText('Update name') as HTMLInputElement).value).toBe('Patch 1 revised');
});
it('retries a failed initial list without showing an editable empty list',async()=>{
 vi.stubGlobal('fetch',vi.fn().mockRejectedValueOnce(Error('Offline')).mockResolvedValueOnce(Response.json([])));render(<UpdatesWorkspace id="game"/>);await screen.findByRole('alert');expect(screen.queryByLabelText('Update name')).toBeNull();await userEvent.click(screen.getByRole('button',{name:'Reload updates'}));await screen.findByText('No updates saved yet. Add a release date above.');
});
