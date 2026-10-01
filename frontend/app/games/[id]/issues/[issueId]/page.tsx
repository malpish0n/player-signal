import { IssuesWorkspace } from '../workspace';
export default async function Page({params,searchParams}:{params:Promise<{id:string;issueId:string}>;searchParams:Promise<{page?:string}>}){const {id,issueId}=await params;const {page}=await searchParams;return <IssuesWorkspace key={`${id}/${issueId}/${page}`} id={id} issueId={issueId} page={page??'0'}/>;}
