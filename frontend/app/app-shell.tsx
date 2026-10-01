'use client';
import Link from 'next/link';
import {usePathname} from 'next/navigation';
import {useState} from 'react';
import {Activity,LayoutDashboard,Layers3,MessageSquareText,Library,FlaskConical,Menu,X,ArrowUpRight} from 'lucide-react';
import {Button} from '@/components/ui/button';
import {AccountControl} from './account-control';
import {WorkspaceAccess} from './workspace-access';
export function AppShell({children}:{children:React.ReactNode}){
 const path=usePathname(),id=path.match(/^\/games\/([^/]+)/)?.[1],demo=path.startsWith('/demo');
 const [open,setOpen]=useState(false);
 const links=id?[{href:`/games/${id}/overview`,label:'Overview',icon:LayoutDashboard},{href:`/games/${id}/issues`,label:'Issues',icon:Layers3},{href:`/games/${id}`,label:'Reviews',icon:MessageSquareText},{href:`/games/${id}/processing`,label:'Processing',icon:Activity}]:demo?[{href:'/demo/overview',label:'Overview',icon:LayoutDashboard},{href:'/demo/issues',label:'Issues & evidence',icon:Layers3},{href:'/demo',label:'Classification',icon:MessageSquareText}]:[];
 return <div className="app-shell"><header className="app-header"><div className="header-brand"><Button variant="ghost" size="icon" className="mobile-menu" aria-label={open?'Close navigation':'Open navigation'} aria-expanded={open} aria-controls="workspace-sidebar" onClick={()=>setOpen(v=>!v)}>{open?<X/>:<Menu/>}</Button><Link className="brand" href="/"><span className="brand-symbol"><Activity size={20}/></span>PlayerSignal</Link><span className="header-divider">/</span><span className="muted">{demo?'Demo workspace':'Workspace'}</span></div><AccountControl/><span className={`badge ${demo?'demo-badge':''}`}>{demo?'Synthetic data':'Internal alpha'}</span></header>
 <div className="app-body"><aside id="workspace-sidebar" className={`sidebar ${open?'is-open':''}`} onKeyDown={e=>{if(e.key==='Escape')setOpen(false);}}><div className="sidebar-label">WORKSPACE</div><nav aria-label="Main navigation">{links.map(({href,label,icon:Icon})=>{const active=href===`/games/${id}`||href==='/demo'?path===href:path===href||path.startsWith(href+'/');return <Link key={href} href={href} aria-current={active?'page':undefined} onClick={()=>setOpen(false)}><Icon size={18}/>{label}</Link>;})}<Link href="/" aria-current={path==='/'?'page':undefined} onClick={()=>setOpen(false)}><Library size={18}/>Game library</Link></nav><div className="sidebar-bottom"><FlaskConical size={20}/><strong>Explore the workflow</strong><p>See structured feedback and recurring issues using synthetic examples.</p><Link href="/demo/overview" onClick={()=>setOpen(false)}>Open demo <ArrowUpRight size={14}/></Link></div></aside><main id="main">{path==='/'||path.startsWith('/games/')?<WorkspaceAccess key={path}>{children}</WorkspaceAccess>:children}</main></div></div>;
}
