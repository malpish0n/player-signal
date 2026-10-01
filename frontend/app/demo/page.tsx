'use client';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { api, message } from '@/lib/api';
import { parseDemo } from '@/lib/analysis';
import { ClassificationResult } from '@/app/analysis-result';
export default function DemoPage() {
  const [demo, setDemo] = useState<ReturnType<typeof parseDemo> | null>(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    api('analysis/demo', parseDemo, {signal:controller.signal}).then(value => { if(!controller.signal.aborted) {setDemo(value);setError('');} }).catch(error => {if(!controller.signal.aborted) setError(message(error));});
    return () => controller.abort();
  }, [retry]);
  return <><Link className="back-link" href="/">← All games</Link><p><Link href="/demo/issues">Explore grouped issues →</Link></p><div className="eyebrow">SYNTHETIC DEMO</div><h1>From review to structured evidence</h1>
    <p className="warning">Example data with deterministic rules. These are not AI findings or real player reviews.</p>
    <p className="muted">This demo makes no model calls and saves nothing to your games. Confidence values are illustrative.</p>
    {error && <div role="alert"><p className="error">{error}</p><button onClick={() => setRetry(v => v+1)}>Retry demo</button></div>}
    {!demo && !error && <p role="status">Loading examples…</p>}
    {demo?.reviews.map(review => <article className="panel compact" key={review.sourceId}><div className="eyebrow">{review.sourceId} · SYNTHETIC SOURCE</div><p className="review-text">{review.sourceText}</p>{review.result ? <ClassificationResult result={review.result} /> : <p className="muted">Skipped: {review.skippedReason}</p>}<p className="muted">{review.model}</p></article>)}
  </>;
}
