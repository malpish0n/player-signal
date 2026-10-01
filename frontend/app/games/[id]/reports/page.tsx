import {ReportsWorkspace} from './workspace';
export default async function Page({params,searchParams}:{params:Promise<{id:string}>;searchParams:Promise<{report?:string}>}){const {id}=await params;const {report}=await searchParams;return <ReportsWorkspace key={`${id}/${report}`} id={id} selected={report}/>;}
