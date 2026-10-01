import {ReviewsWorkspace} from '../reviews-workspace';
export default async function Page({params}:{params:Promise<{id:string}>}){const {id}=await params;return <ReviewsWorkspace id={id} page="0" language="" vote="" processingOnly/>;}
