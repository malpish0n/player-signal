import type {Metadata} from 'next';
import {SharedReport} from './report';
export const metadata:Metadata={title:'Shared report · PlayerSignal',robots:{index:false,follow:false},referrer:'no-referrer'};
export default async function Page({params}:{params:Promise<{token:string}>}){const {token}=await params;return <SharedReport token={token}/>;}
