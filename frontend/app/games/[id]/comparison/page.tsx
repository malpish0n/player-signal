import {ComparisonWorkspace} from './workspace';
export default async function Page({params,searchParams}:{params:Promise<{id:string}>;searchParams:Promise<{date?:string;days?:string}>}){const {id}=await params;const {date='',days='7'}=await searchParams;return <ComparisonWorkspace key={`${id}/${date}/${days}`} id={id} date={date} days={days}/>;}
