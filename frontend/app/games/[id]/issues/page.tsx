import { IssuesWorkspace } from './workspace';
export default async function Page({params,searchParams}:{params:Promise<{id:string}>;searchParams:Promise<{page?:string;minSeverity?:string}>}){const {id}=await params;const {page,minSeverity}=await searchParams;return <IssuesWorkspace key={`${id}/${page}/${minSeverity}`} id={id} page={page??'0'} minSeverity={minSeverity??'0'}/>;}
