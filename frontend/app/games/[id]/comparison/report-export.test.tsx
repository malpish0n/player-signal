// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {comparisonReport} from '@/lib/comparison-report';
import {Comparison} from '@/lib/comparison';
import {ComparisonReportExport} from './report-export';
const data:Comparison={game:{id:'game',steamAppId:620,name:'Portal <script> & [link](https://evil.test)\n# title',headerImageUrl:null,createdAt:'2026-01-01T00:00:00Z'},date:'2026-09-20',days:7,calculatedAt:'2026-10-01T12:00:00Z',imported:4,before:{from:'2026-09-13',to:'2026-09-19',reviews:0,recommended:0,recommendationRate:null},after:{from:'2026-09-20',to:'2026-09-26',reviews:4,recommended:3,recommendationRate:.75},reviewChangePercent:null,recommendationChangePoints:null};
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.unstubAllGlobals();vi.useRealTimers();});
it('exports exact windows, provenance, missing baselines and safe imported text',()=>{
 const report=comparisonReport(data,'https://playersignal.test');
 expect(report).toContain('| Imported reviews | 0 | 4 |');expect(report).toContain('| Recommendation rate | No reviews | 75.0% |');
 expect(report).toContain('2026-10-01T12:00:00Z');expect(report).toContain('No comparison baseline');
 expect(report).toContain('https://playersignal.test/games/game?from=2026-09-20&to=2026-09-26');
 expect(report).toContain('&lt;script&gt; &amp; \\[link\\]');expect(report).not.toContain('\n# title');
 expect(report).toContain('does not prove');expect(report).toContain('Localhost links');
});
it('distinguishes zero and negative changes from missing data',()=>{
 const report=comparisonReport({...data,reviewChangePercent:0,recommendationChangePoints:-2.3529},'http://localhost:3000');
 expect(report).toContain('Review volume change: 0.0%');expect(report).toContain('Recommendation rate change: -2.4 percentage points');
});
it('downloads the displayed snapshot and releases the object URL',()=>{
 vi.useFakeTimers();const create=vi.fn().mockReturnValue('blob:report'),revoke=vi.fn();
 vi.stubGlobal('URL',class extends URL {static createObjectURL=create;static revokeObjectURL=revoke;});
 let filename='';vi.spyOn(HTMLAnchorElement.prototype,'click').mockImplementation(function(this:HTMLAnchorElement){filename=this.download;});
 render(<ComparisonReportExport data={data}/>);fireEvent.click(screen.getByRole('button'));
 expect(filename).toBe('playersignal-620-comparison-2026-09-20-7d.md');expect(create.mock.calls[0][0].type).toBe('text/markdown;charset=utf-8');
 expect(document.querySelector('a[download]')).toBeNull();vi.runAllTimers();expect(revoke).toHaveBeenCalledWith('blob:report');
});
it('allows retry after browser download failure',()=>{
 vi.stubGlobal('URL',class extends URL {static createObjectURL():string{throw Error('download unavailable');}});
 render(<ComparisonReportExport data={data}/>);fireEvent.click(screen.getByRole('button'));expect(screen.getByRole('alert').textContent).toContain('Please try again');expect(screen.getByRole('button').hasAttribute('disabled')).toBe(false);
});
