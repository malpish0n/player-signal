'use client';
import {useState} from 'react';
import {Comparison} from '@/lib/comparison';
import {comparisonReport} from '@/lib/comparison-report';
export function ComparisonReportExport({data}:{data:Comparison}) {
 const [error,setError]=useState('');
 function download(){
  setError('');
  try {
   const blob=new Blob([comparisonReport(data,window.location.origin)],{type:'text/markdown;charset=utf-8'});
   const url=URL.createObjectURL(blob),link=document.createElement('a');
   try {link.href=url;link.download=`playersignal-${data.game.steamAppId}-comparison-${data.date}-${data.days}d.md`;document.body.appendChild(link);link.click();}
   finally {link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);}
  } catch {setError('Could not download the report. Please try again.');}
 }
 return <div className="review-export"><button onClick={download}>Download report (.md)</button><p className="muted">Exports the displayed results and source links as Markdown. Workspace access is required to open the links.</p>{error&&<p role="alert" className="error">{error}</p>}</div>;
}
