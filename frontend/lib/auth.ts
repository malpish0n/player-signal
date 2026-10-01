export type Session={enabled:boolean;user:{id:string;email:string;workspaceId:string;workspaceName:string}|null;csrfToken:string|null};
export function parseSession(value:unknown):Session{
 if(!value||typeof value!=='object'||!('enabled'in value)||typeof value.enabled!=='boolean'||!('user'in value)||!('csrfToken'in value))throw Error('Invalid account session.');
 const d=value as Record<string,unknown>;let user:Session['user']=null;
 if(d.user!==null){if(typeof d.user!=='object'||!d.user)throw Error('Invalid user.');const u=d.user as Record<string,unknown>;for(const field of ['id','email','workspaceId','workspaceName'])if(typeof u[field]!=='string')throw Error('Invalid user.');user=u as Session['user'];}
 if(d.csrfToken!==null&&typeof d.csrfToken!=='string')throw Error('Invalid session token.');return {enabled:d.enabled as boolean,user,csrfToken:d.csrfToken as string|null};
}
