import {Comparison,parseComparison} from './comparison';
export type ReportSummary={id:string;type:string;periodStart:string;periodEnd:string;createdAt:string};
export type Report={report:ReportSummary;payload:{comparison:Comparison;summary:string[];method:string;evidence:{issueId:string;title:string;sourceText:string;steamReviewId:string;model:string;promptVersion:string}[]}};
function object(v:unknown):Record<string,unknown>{if(!v||typeof v!=='object'||Array.isArray(v))throw Error('Invalid report.');return v as Record<string,unknown>;}
function text(v:unknown):string{if(typeof v!=='string')throw Error('Invalid report text.');return v;}
function summary(v:unknown):ReportSummary{const d=object(v);return {id:text(d.id),type:text(d.type),periodStart:text(d.periodStart),periodEnd:text(d.periodEnd),createdAt:text(d.createdAt)};}
export function parseReports(v:unknown):ReportSummary[]{if(!Array.isArray(v))throw Error('Invalid report list.');return v.map(summary);}
export function parseReport(v:unknown):Report{const d=object(v),p=object(d.payload);if(!Array.isArray(p.summary)||!Array.isArray(p.evidence))throw Error('Invalid report payload.');return {report:summary(d.report),payload:{comparison:parseComparison(p.comparison),summary:p.summary.map(text),method:text(p.method),evidence:p.evidence.map(v=>{const e=object(v);return {issueId:text(e.issueId),title:text(e.title),sourceText:text(e.sourceText),steamReviewId:text(e.steamReviewId),model:text(e.model),promptVersion:text(e.promptVersion)};})}};}
