export type GameUpdate={id:string;title:string;releasedOn:string;version:number;createdAt:string;updatedAt:string};
export function parseUpdate(value:unknown):GameUpdate {
 if(!value||typeof value!=='object')throw Error('Invalid update response.');
 const d=value as Record<string,unknown>;
 for(const field of ['id','title','releasedOn','createdAt','updatedAt'])if(typeof d[field]!=='string')throw Error('Invalid update response.');
 if(typeof d.version!=='number'||!Number.isSafeInteger(d.version)||d.version<0||!/^\d{4}-\d{2}-\d{2}$/.test(d.releasedOn as string)||!Number.isFinite(Date.parse(d.releasedOn as string)))throw Error('Invalid update response.');
 return d as GameUpdate;
}
export function parseUpdates(value:unknown):GameUpdate[]{if(!Array.isArray(value))throw Error('Invalid update list.');return value.map(parseUpdate);}
