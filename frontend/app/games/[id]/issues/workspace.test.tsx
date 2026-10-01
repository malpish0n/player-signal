// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,render,screen} from '@testing-library/react';
import {IssuesWorkspace} from './workspace';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('sends selected filters and sort, retaining them in pagination links',async()=>{
 const game={id:'game',name:'Fixture',steamAppId:620,headerImageUrl:null,createdAt:'2026-10-01T00:00:00Z'};
 const metrics={mentionCount:2,firstSeenAt:'2026-09-01T00:00:00Z',lastSeenAt:'2026-10-01T00:00:00Z',negativeRatio:1,recentMentions:2,previousMentions:0,velocityPercent:null,severityScore:75,confidence:.9};
 const data={snapshot:{builtAt:'2026-10-01T00:00:00Z',algorithm:'fixture',threshold:.72,clusteredReviews:6,eligibleReviews:6,stale:false},items:[{id:'issue',title:'Example issue',category:'BUG',status:'OPEN',metrics}],page:1,size:1,total:3};
 const fetch=vi.fn(async(url:string)=>Response.json(url.includes('/issues?')?data:game));vi.stubGlobal('fetch',fetch);
 render(<IssuesWorkspace id="game" page="1" minSeverity="75" category="BUG" sort="growth"/>);
 await screen.findByText('Example issue');
 expect(fetch.mock.calls.map(c=>c[0])).toContain('/api/games/game/issues?page=1&minSeverity=75&category=BUG&sort=growth');
 expect((screen.getByLabelText('Category') as HTMLSelectElement).value).toBe('BUG');expect((screen.getByLabelText('Sort by') as HTMLSelectElement).value).toBe('growth');
 for(const [name,page] of [['Previous','0'],['Next','2']]){const url=new URL(screen.getByRole('link',{name}).getAttribute('href')!,'http://localhost');expect(url.searchParams.get('page')).toBe(page);expect(url.searchParams.get('category')).toBe('BUG');expect(url.searchParams.get('sort')).toBe('growth');expect(url.searchParams.get('minSeverity')).toBe('75');}
 expect(screen.getByText('No baseline')).toBeTruthy();
});
