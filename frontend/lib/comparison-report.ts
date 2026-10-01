import {Comparison,comparisonReviews} from './comparison';

// Treat imported game names as plain text even in Markdown/HTML renderers.
function plain(value:string){return value.replace(/[\r\n\u0000-\u001f\u007f]/g,' ').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/[\\`*_{}\[\]()#+.!|~-]/g,'\\$&');}
function change(value:number|null,unit:string){return value===null?'No comparison baseline':`${value>0?'+':''}${value.toFixed(1)}${unit}`;}
export function comparisonReport(data:Comparison,origin:string):string {
 const base=new URL(origin).origin;
 const rate=(value:number|null)=>value===null?'No reviews':`${(value*100).toFixed(1)}%`;
 return `# PlayerSignal — before & after report

Game: ${plain(data.game.name)} (Steam App ${data.game.steamAppId})
Comparison date: ${data.date} (UTC)
Window: ${data.days} days on each side
Calculated at: ${data.calculatedAt}
Imported reviews stored: ${data.imported}

| Metric | Before | After |
| --- | --- | --- |
| Dates (UTC, inclusive) | ${data.before.from} – ${data.before.to} | ${data.after.from} – ${data.after.to} |
| Imported reviews | ${data.before.reviews} | ${data.after.reviews} |
| Recommended | ${data.before.recommended} | ${data.after.recommended} |
| Recommendation rate | ${rate(data.before.recommendationRate)} | ${rate(data.after.recommendationRate)} |

Review volume change: ${change(data.reviewChangePercent,'%')}
Recommendation rate change: ${change(data.recommendationChangePoints,' percentage points')}

## Source evidence

- [Before period](<${base}${comparisonReviews(data.game.id,data.before)}>)
- [After period](<${base}${comparisonReviews(data.game.id,data.after)}>)

These links require access to the original PlayerSignal workspace. Localhost links work only on the machine hosting the app. Review lists can change after subsequent imports; the figures above reflect the calculation time.

## Interpretation and limits

Only imported reviews are included. Missing imports and small samples can distort comparisons. Both windows contain complete UTC days; the selected date begins the after period.

Recommendations are Steam votes, not AI sentiment. Changes compare after with before. A missing baseline is not zero change. This report does not prove that an update caused a change and does not contain an AI-generated assessment.
`;
}
