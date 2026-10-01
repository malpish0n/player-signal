import {Game,parseGame} from './api';
export type ComparisonWindow={from:string;to:string;reviews:number;recommended:number;recommendationRate:number|null};
export type Comparison={game:Game;date:string;days:number;calculatedAt:string;imported:number;before:ComparisonWindow;after:ComparisonWindow;reviewChangePercent:number|null;recommendationChangePoints:number|null};
function obj(v:unknown):Record<string,unknown>{if(!v||typeof v!=='object'||Array.isArray(v))throw Error('Invalid comparison.');return v as Record<string,unknown>;}
function date(v:unknown):string{if(typeof v!=='string'||!Number.isFinite(Date.parse(v)))throw Error('Invalid comparison date.');return v;}
function count(v:unknown):number{if(typeof v!=='number'||!Number.isSafeInteger(v)||v<0)throw Error('Invalid comparison count.');return v;}
function metric(v:unknown):number|null{if(v===null)return null;if(typeof v!=='number'||!Number.isFinite(v))throw Error('Invalid comparison metric.');return v;}
function window(v:unknown):ComparisonWindow{const d=obj(v),reviews=count(d.reviews),recommended=count(d.recommended),rate=metric(d.recommendationRate);if(recommended>reviews||(rate!==null&&(rate<0||rate>1))||(reviews===0)!==(rate===null))throw Error('Inconsistent comparison.');return {from:date(d.from),to:date(d.to),reviews,recommended,recommendationRate:rate};}
export function parseComparison(v:unknown):Comparison{const d=obj(v),days=count(d.days);if(![7,30,90].includes(days))throw Error('Invalid comparison window.');return {game:parseGame(d.game),date:date(d.date),days,calculatedAt:date(d.calculatedAt),imported:count(d.imported),before:window(d.before),after:window(d.after),reviewChangePercent:metric(d.reviewChangePercent),recommendationChangePoints:metric(d.recommendationChangePoints)};}
export function comparisonReviews(id:string,w:ComparisonWindow){return `/games/${id}?${new URLSearchParams({from:w.from,to:w.to})}`;}
