"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, Game, message, parseGame, parseGames } from "@/lib/api";

export function GameSetup() {
  const router = useRouter();
  const [games, setGames] = useState<Game[] | null>(null);
  const [error, setError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [busy, setBusy] = useState(false);
  async function load() {
    setError("");
    try { setGames(await api("games", parseGames)); } catch (error) { setError(message(error)); }
  }
  useEffect(() => {
    const controller = new AbortController();
    api("games", parseGames, { signal: controller.signal }).then(setGames).catch(error => {
      if (!controller.signal.aborted) setError(message(error));
    });
    return () => controller.abort();
  }, []);
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input = new FormData(event.currentTarget).get("steamApp");
    setBusy(true); setSubmitError("");
    try {
      const game = await api("games", parseGame, { method: "POST", body: JSON.stringify({ steamApp: input }) });
      router.push(`/games/${game.id}/overview`);
    } catch (error) { setSubmitError(message(error)); setBusy(false); }
  }
  return <>
    <section className="panel">
      <h2>Connect a game</h2>
      <form onSubmit={submit}>
        <label htmlFor="steamApp">Steam App ID or store URL</label>
        <div className="form-row">
          <input id="steamApp" name="steamApp" required maxLength={2048} placeholder="620 or https://store.steampowered.com/app/620/" aria-describedby="game-help" />
          <button className="primary" disabled={busy}>{busy ? "Finding game…" : "Connect game"}</button>
        </div>
        <p className="muted" id="game-help">We resolve the game first. You can start importing after reviewing its details.</p>
        {submitError && <p className="error" role="alert">{submitError}</p>}
      </form>
    </section>
    <section className="panel">
      <h2>Your games</h2>
      {error && <div role="alert"><p className="error">{error}</p><button onClick={load}>Retry loading</button></div>}
      {games === null && !error && <p role="status">Loading connected games…</p>}
      {games?.length === 0 && <p className="muted">No games connected yet. Add an App ID above to begin.</p>}
      {games && <ul className="game-list">{games.map(game => <li key={game.id}>
        <Link href={`/games/${game.id}/overview`}><strong>{game.name}</strong><span>App {game.steamAppId} → Open overview</span></Link>
      </li>)}</ul>}
    </section>
  </>;
}
