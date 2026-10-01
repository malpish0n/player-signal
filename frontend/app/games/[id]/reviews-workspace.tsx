"use client";
import Link from "next/link";
import {UsagePanel} from "@/app/usage-panel";
import { useCallback, useEffect, useState } from "react";
import { api, Game, Run, ReviewPage, message, parseGame, parseRun, parseReviews } from "@/lib/api";

import { AnalysisStatus, parseAnalysisStatus, parseAnalysisRun } from '@/lib/analysis';
import { ReviewExport } from "@/app/review-export";
import { AnalysisHistory } from "@/app/analysis-history";
import { AnalysisResult } from '@/app/analysis-result';

export function ReviewsWorkspace({ id, page, language, vote, q = "", from = "", to = "", processingOnly = false }: { id: string; page: string; language: string; vote: string; q?: string; from?: string; to?: string; processingOnly?: boolean }) {
  const [analysis, setAnalysis] = useState<AnalysisStatus | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [game, setGame] = useState<Game | null>(null);
  const [runLoaded, setRunLoaded] = useState(false);
  const [run, setRun] = useState<Run | null>(null);
  const [reviews, setReviews] = useState<ReviewPage | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [retry, setRetry] = useState(0);
  const query = new URLSearchParams({ page, size: "20" });
  if (language) query.set("language", language);
  if (q) query.set("q", q);
  if (vote) query.set("votedUp", vote);
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  const reviewQuery = query.toString();
  const load = useCallback(async (signal: AbortSignal) => {
    const results = await Promise.allSettled([
      api(`games/${id}`, parseGame, { signal }),
      api(`games/${id}/sync/latest`, parseRun, { signal }),
      api(`games/${id}/reviews?${reviewQuery}`, parseReviews, { signal }),
      api(`games/${id}/analysis`, parseAnalysisStatus, { signal }),
    ]);
    if (signal.aborted) return;
    if (results[0].status === "fulfilled") setGame(results[0].value);
    if (results[1].status === "fulfilled") { setRun(results[1].value); setRunLoaded(true); }
    if (results[2].status === "fulfilled") setReviews(results[2].value);
    if (results[3].status === "fulfilled") setAnalysis(results[3].value);
    const failed = results.find(result => result.status === "rejected");
    setError(failed?.status === "rejected" ? message(failed.reason) : "");
  }, [id, reviewQuery]);
  useEffect(() => {
    const controller = new AbortController();
    // load updates state only after awaiting network responses.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load(controller.signal);
    return () => controller.abort();
  }, [load, retry]);
  useEffect(() => {
    if (run?.status !== "RUNNING" && analysis?.latestRun?.status !== "RUNNING") return;
    const controller = new AbortController();
    const timer = setTimeout(() => void load(controller.signal), 2000);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [run, analysis, load]);
  async function sync() {
    setBusy(true); setError("");
    try { setRun(await api(`games/${id}/sync`, parseRun, { method: "POST" })); }
    catch (error) { setError(message(error)); }
    finally { setBusy(false); }
  }
  async function analyze(retryFailed = false) {
    setAnalyzing(true); setError("");
    try {
      const latestRun = await api(`games/${id}/analysis?retryFailed=${retryFailed}`, parseAnalysisRun, { method: 'POST' });
      setAnalysis(current => current ? { ...current, latestRun } : current);
      setRetry(value => value + 1);
    } catch (error) { setError(message(error)); }
    finally { setAnalyzing(false); }
  }
  function pageUrl(next: number) {
    const filters = new URLSearchParams({ page: String(next) });
    if (language) filters.set("language", language);
    if (q) filters.set("q", q);
    if (vote) filters.set("vote", vote);
    if (from) filters.set("from", from);
    if (to) filters.set("to", to);
    return `/games/${id}?${filters}`;
  }
  const filtered = Boolean(language || vote || q || from || to);
  return <>
    <div className="workspace-nav"><Link href="/">← All games</Link><Link href={`/games/${id}/issues`}>Issues & evidence →</Link></div>
    <div className="page-heading">
      <div><div className="eyebrow">{processingOnly ? "PROCESSING" : "REVIEW EXPLORER"}</div><h1>{game?.name ?? "Game workspace"}</h1>
        <p className="muted">{game ? `Steam App ${game.steamAppId}` : "Loading game…"} · Original player feedback</p>
      </div>
      <button className="primary" disabled={!game || busy || run?.status === "RUNNING"} onClick={sync}>
        {busy ? "Starting…" : run?.status === "RUNNING" ? "Importing…" : run?.status === "PARTIAL" || run?.status === "FAILED" ? "Resume import" : "Sync reviews"}
      </button>
    </div>
    {error && <div className="panel" role="alert"><p className="error">{error}</p><p className="muted">Any displayed data may be stale. Previously saved reviews remain available.</p><button onClick={() => setRetry(value => value + 1)}>Retry loading</button></div>}
    <section className="panel compact" aria-label="Import status">
      <h2>Import status</h2>
      {!runLoaded ? <p role="status">Loading import status…</p> : !run ? <p className="muted">No import has run yet. Start a sync to collect public Steam reviews.</p> : <>
        <p role="status"><span className="badge">{run.status}</span> {run.fetched} fetched · {run.inserted} added · {run.updated} updated in this run</p>
        <p className="muted">{run.status === "RUNNING" ? "Importing in the background. Counts refresh as each page is saved." : `Last run finished: ${new Date(run.finishedAt ?? run.startedAt).toLocaleString()}`}</p>
        {run.status === "PARTIAL" && <p className="warning">This import reached its page limit. Resume to collect the next batch; the dataset is not complete yet.</p>}
        {run.error && <p className="error">{run.error}</p>}
      </>}

    </section>
    <section className="panel compact" aria-label="Review analysis">
      <h2>Review analysis</h2>
      {!analysis ? <p role="status">Loading analysis status…</p> : <>
        <p>{analysis.counts.succeeded} analyzed · {analysis.counts.pending} pending · {analysis.counts.running} running · {analysis.counts.skipped} skipped · {analysis.counts.failed} failed</p>
        {!analysis.available && <p className="warning">Analysis is disabled or not configured. Imported reviews remain pending. <Link href="/demo">Explore the synthetic demo →</Link></p>}
        <p className="muted">{analysis.provider === "local-rules" ? "Local English phrase rules, not AI. Low-confidence matches need review; unclassified is not neutral sentiment. " : ""}{analysis.model} · Up to {analysis.maxReviewsPerRun} reviews per batch, across all pages and filters. Model suggestions require human review.</p>
        {analysis.latestRun && <p role="status">Latest batch: {analysis.latestRun.status} · {analysis.latestRun.processed} processed · {analysis.latestRun.cached} reused{analysis.latestRun.error ? ` · ${analysis.latestRun.error}` : ''}</p>}
        <div className="analysis-actions"><button disabled={!analysis.available || analyzing || analysis.latestRun?.status === 'RUNNING' || !analysis.counts.pending} onClick={() => void analyze()}>Analyze next batch</button>
        {analysis.counts.failed > 0 && <button disabled={!analysis.available || analyzing || analysis.latestRun?.status === 'RUNNING'} onClick={() => void analyze(true)}>Retry failed + pending</button>}</div>
      </>}
    </section>
    {processingOnly && <UsagePanel id={id}/>}
    {!processingOnly && <section className="panel">
      <div className="page-heading"><h2>Reviews {reviews ? `· ${reviews.total.toLocaleString()}${filtered ? " matching" : " imported"}` : ""}</h2></div>
      <form className="filters" action={`/games/${id}`} key={`${language}/${vote}/${q}/${from}/${to}`}>
        <div><label htmlFor="review-search">Search review text</label><input id="review-search" name="q" defaultValue={q} maxLength={256} placeholder="Search original feedback…" /></div>
        <div><label htmlFor="vote">Recommendation</label><select id="vote" name="vote" defaultValue={vote}><option value="">All reviews</option><option value="true">Recommended</option><option value="false">Not recommended</option></select></div>
        <div><label htmlFor="language">Language code</label><input id="language" name="language" defaultValue={language} placeholder="All languages" pattern="[a-z]{2,32}" maxLength={32} /></div>
        <div><label htmlFor="review-from">From (UTC)</label><input id="review-from" name="from" type="date" defaultValue={from} min="0001-01-01" max="9999-12-31"/></div>
        <div><label htmlFor="review-to">To (UTC, inclusive)</label><input id="review-to" name="to" type="date" defaultValue={to} min="0001-01-01" max="9999-12-31"/></div>
        <button type="submit">Apply filters</button>
        {filtered && <Link href={`/games/${id}`}>Clear filters</Link>}
      </form>
      <ReviewExport id={id} language={language} vote={vote} q={q} from={from} to={to}/>
      {!reviews && !error && <p role="status">Loading reviews…</p>}
      {reviews?.items.length === 0 && <p className="muted">{filtered ? "No reviews match these filters. Clear them to see all imported feedback." : "No reviews on this page. Run an import or return to the first page."}</p>}
      <div className="review-list">{reviews?.items.map(review => <article key={review.id} className="review">
        <div className="review-meta"><strong className={review.votedUp ? "healthy" : "warning"}>{review.votedUp ? "Recommended" : "Not recommended"}</strong><span>{review.language}</span><span>{(review.playtimeMinutes / 60).toFixed(1)} h played</span><time dateTime={review.createdAtSteam}>{new Date(review.createdAtSteam).toLocaleDateString(undefined, {timeZone:"UTC"})}</time></div>
        <p className="review-text">{review.reviewText || "(No written review text)"}</p>
        <div className="muted">Steam review #{review.steamRecommendationId} · {review.votesUp} helpful votes</div>
        <AnalysisResult analysis={review.analysis} />
        <AnalysisHistory gameId={id} reviewId={review.id} currentId={review.analysis?.id??null}/>
      </article>)}</div>
      {reviews && <nav className="pagination" aria-label="Review pages">
        {reviews.page > 0 && <Link className="button" href={pageUrl(reviews.page - 1)}>Previous</Link>}
        <span>Page {reviews.page + 1} · 20 per page</span>
        {(reviews.page + 1) * reviews.size < reviews.total && <Link className="button" href={pageUrl(reviews.page + 1)}>Next</Link>}
      </nav>}
    </section>}
  </>;
}
