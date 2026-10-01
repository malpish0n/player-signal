import { Classification, parseClassification } from './analysis';
export type Metrics = {mentionCount:number; firstSeenAt:string; lastSeenAt:string; negativeRatio:number; recentMentions:number; previousMentions:number; velocityPercent:number|null; severityScore:number; confidence:number};
export type Issue = {id:string;title:string;category:string;status:string;metrics:Metrics};
export type Snapshot = {builtAt:string|null;algorithm:string;threshold:number;clusteredReviews:number;eligibleReviews:number;stale:boolean};
export type Evidence = {analysisId:string;reviewId:string;steamRecommendationId:string;sourceText:string;language:string;seenAt:string;classification:Classification;similarity:number;model:string;promptVersion:string};
export type IssuePage = {snapshot:Snapshot;items:Issue[];page:number;size:number;total:number};
export type IssueDetail = {snapshot:Snapshot;issue:Issue;evidence:Evidence[];page:number;size:number;total:number};
function obj(v:unknown):Record<string,unknown>{if(!v||typeof v!=='object'||Array.isArray(v))throw Error('Invalid issue response.');return v as Record<string,unknown>;}
function str(v:unknown):string{if(typeof v!=='string')throw Error('Invalid issue text.');return v;}
function num(v:unknown,min=-Infinity,max=Infinity):number{if(typeof v!=='number'||!Number.isFinite(v)||v<min||v>max)throw Error('Invalid issue metric.');return v;}
function count(v:unknown):number{const n=num(v,0);if(!Number.isSafeInteger(n))throw Error('Invalid issue count.');return n;}
function date(v:unknown):string{const s=str(v);if(!Number.isFinite(Date.parse(s)))throw Error('Invalid issue date.');return s;}
function list<T>(v:unknown,parse:(v:unknown)=>T):T[]{if(!Array.isArray(v))throw Error('Invalid issue list.');return v.map(parse);}
function metrics(v:unknown):Metrics{const d=obj(v);return {mentionCount:count(d.mentionCount),firstSeenAt:date(d.firstSeenAt),lastSeenAt:date(d.lastSeenAt),negativeRatio:num(d.negativeRatio,0,1),recentMentions:count(d.recentMentions),previousMentions:count(d.previousMentions),velocityPercent:d.velocityPercent===null?null:num(d.velocityPercent,-100),severityScore:num(d.severityScore,0,100),confidence:num(d.confidence,0,1)};}
function issue(v:unknown):Issue{const d=obj(v);return {id:str(d.id),title:str(d.title),category:str(d.category),status:str(d.status),metrics:metrics(d.metrics)};}
function snapshot(v:unknown):Snapshot{const d=obj(v);if(typeof d.stale!=='boolean')throw Error('Invalid snapshot state.');return {builtAt:d.builtAt===null?null:date(d.builtAt),algorithm:str(d.algorithm),threshold:num(d.threshold,0,1),clusteredReviews:count(d.clusteredReviews),eligibleReviews:count(d.eligibleReviews),stale:d.stale};}
function evidence(v:unknown):Evidence{const d=obj(v);return {analysisId:str(d.analysisId),reviewId:str(d.reviewId),steamRecommendationId:str(d.steamRecommendationId),sourceText:str(d.sourceText),language:str(d.language),seenAt:date(d.seenAt),classification:parseClassification(d.classification),similarity:num(d.similarity,-1,1),model:str(d.model),promptVersion:str(d.promptVersion)};}
export function parseIssuePage(v:unknown):IssuePage{const d=obj(v);return {snapshot:snapshot(d.snapshot),items:list(d.items,issue),page:count(d.page),size:count(d.size),total:count(d.total)};}
export function parseIssueDetail(v:unknown):IssueDetail{const d=obj(v);return {snapshot:snapshot(d.snapshot),issue:issue(d.issue),evidence:list(d.evidence,evidence),page:count(d.page),size:count(d.size),total:count(d.total)};}
export function parseIssueDemo(v:unknown){const d=obj(v);if(d.mode!=='SYNTHETIC_DEMO')throw Error('Invalid synthetic demo.');return {asOf:date(d.asOf),algorithm:str(d.algorithm),issues:list(d.issues,v=>{const i=obj(v);return {issue:issue(i.issue),evidence:list(i.evidence,evidence)};})};}
export function growth(m:Metrics):string{return m.velocityPercent===null?'No prior-week baseline':`${m.velocityPercent>0?'+':''}${m.velocityPercent.toFixed(0)}% vs previous 7 days`;}
