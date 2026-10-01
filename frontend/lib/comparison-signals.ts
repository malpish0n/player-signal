export type SignalChange={id:string;title:string;category:string;before:number;after:number;beforeShare:number|null;afterShare:number|null;changePoints:number|null;movement:string};
export type Signals={provider:string;model:string;analyzedBefore:number;analyzedAfter:number;snapshot:{stale:boolean;builtAt:string|null};categories:SignalChange[];issues:SignalChange[];daily:{date:string;reviews:number;recommended:number}[]};
function obj(v:unknown):Record<string,unknown>{if(!v||typeof v!=='object'||Array.isArray(v))throw Error('Invalid signals.');return v as Record<string,unknown>;}
function text(v:unknown):string{if(typeof v!=='string')throw Error('Invalid signal text.');return v;}
function count(v:unknown):number{if(typeof v!=='number'||!Number.isSafeInteger(v)||v<0)throw Error('Invalid signal count.');return v;}
function metric(v:unknown):number|null{if(v===null)return null;if(typeof v!=='number'||!Number.isFinite(v))throw Error('Invalid signal metric.');return v;}
export function parseSignals(v:unknown):Signals{const d=obj(v),s=obj(d.snapshot);if(typeof s.stale!=='boolean'||!Array.isArray(d.daily))throw Error('Invalid signal snapshot.');
 function changes(value:unknown):SignalChange[]{if(!Array.isArray(value))throw Error('Invalid signals.');return value.map(v=>{const c=obj(v);return {id:text(c.id),title:text(c.title),category:text(c.category),before:count(c.before),after:count(c.after),beforeShare:metric(c.beforeShare),afterShare:metric(c.afterShare),changePoints:metric(c.changePoints),movement:text(c.movement)};});}
 return {provider:text(d.provider),model:text(d.model),analyzedBefore:count(d.analyzedBefore),analyzedAfter:count(d.analyzedAfter),snapshot:{stale:s.stale,builtAt:s.builtAt===null?null:text(s.builtAt)},categories:changes(d.categories),issues:changes(d.issues),daily:d.daily.map(v=>{const row=obj(v);return {date:text(row.date),reviews:count(row.reviews),recommended:count(row.recommended)};})};
}
