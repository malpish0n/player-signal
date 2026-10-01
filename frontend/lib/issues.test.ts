import {expect,it} from 'vitest';
import {growth,parseIssuePage,parseIssueDemo,Metrics} from './issues';
const metrics:Metrics={mentionCount:2,firstSeenAt:'2026-09-22T12:00:00Z',lastSeenAt:'2026-09-29T12:00:00Z',negativeRatio:1,recentMentions:1,previousMentions:1,velocityPercent:0,severityScore:75,confidence:.8};
const page={snapshot:{builtAt:null,algorithm:'lexical-centroid-v1',threshold:.72,clusteredReviews:0,eligibleReviews:0,stale:true},items:[],page:0,size:20,total:0};
it('preserves missing snapshot and stale state',()=>{expect(parseIssuePage(page).snapshot.builtAt).toBeNull();expect(parseIssuePage(page).snapshot.stale).toBe(true);});
it('distinguishes zero growth from absent baseline',()=>{expect(growth(metrics)).toBe('0% vs previous 7 days');expect(growth({...metrics,velocityPercent:null,previousMentions:0})).toBe('No prior-week baseline');});
it('rejects impossible metrics instead of showing misleading scores',()=>{for(const patch of [{negativeRatio:2},{severityScore:101},{mentionCount:1.5},{velocityPercent:NaN}])expect(()=>parseIssuePage({...page,items:[{id:'id',title:'Title',category:'BUG',status:'OPEN',metrics:{...metrics,...patch}}]})).toThrow();});
it('requires synthetic mode for the demo',()=>{expect(()=>parseIssueDemo({mode:'LIVE',issues:[]})).toThrow();});
