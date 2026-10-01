import {OverviewWorkspace} from '@/components/dashboard/overview-workspace';
export default async function Page({params,searchParams}:{params:Promise<{id:string}>;searchParams:Promise<{days?:string}>}){const {id}=await params;const {days}=await searchParams;return <OverviewWorkspace key={`${id}/${days}`} id={id} days={days}/>;}
