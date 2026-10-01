// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,render,screen} from '@testing-library/react';
import {ReviewsWorkspace} from './reviews-workspace';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('keeps UTC date bounds in the request, form and both pagination links',async()=>{
 const fetch=vi.fn(async(url:string)=>{
  if(url.includes('/reviews?'))return Response.json({items:[],total:61,page:1,size:20});
  if(url.endsWith('/sync/latest'))return new Response(null,{status:204});
  if(url.endsWith('/analysis'))return Response.json({available:false,provider:'disabled',model:'test',promptVersion:'v1',maxReviewsPerRun:25,counts:{total:61,pending:61,running:0,succeeded:0,skipped:0,failed:0},latestRun:null});
  return Response.json({id:'game',steamAppId:620,name:'Fixture',headerImageUrl:null,createdAt:'2026-10-01T00:00:00Z'});
 });vi.stubGlobal('fetch',fetch);
 render(<ReviewsWorkspace id="game" page="1" language="english" vote="false" q="crash" from="2026-09-01" to="2026-09-30"/>);
 await screen.findByRole('navigation',{name:'Review pages'});
 expect(fetch.mock.calls.map(c=>c[0])).toContain('/api/games/game/reviews?page=1&size=20&language=english&q=crash&votedUp=false&from=2026-09-01&to=2026-09-30');
 expect((screen.getByLabelText('From (UTC)') as HTMLInputElement).value).toBe('2026-09-01');expect((screen.getByLabelText('To (UTC, inclusive)') as HTMLInputElement).value).toBe('2026-09-30');
 for(const label of ['Previous','Next']){const url=new URL(screen.getByRole('link',{name:label}).getAttribute('href')!,'http://localhost');expect(url.searchParams.get('from')).toBe('2026-09-01');expect(url.searchParams.get('to')).toBe('2026-09-30');expect(url.searchParams.get('q')).toBe('crash');}
 expect(screen.getByRole('link',{name:'Clear filters'}).getAttribute('href')).toBe('/games/game');
});
