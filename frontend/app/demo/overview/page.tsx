import {OverviewWorkspace} from '@/components/dashboard/overview-workspace';
export default async function Page({searchParams}:{searchParams:Promise<{days?:string}>}){const {days}=await searchParams;return <OverviewWorkspace key={days} demo days={days}/>;}
