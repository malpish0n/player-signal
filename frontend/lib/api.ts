import { Analysis, parseAnalysis } from "./analysis";
export type Game = { id: string; steamAppId: number; name: string; headerImageUrl: string | null; createdAt: string };
export type Run = { id: string; status: "RUNNING" | "COMPLETED" | "PARTIAL" | "FAILED"; fetched: number; inserted: number; updated: number; startedAt: string; finishedAt: string | null; error: string | null };
export type Review = { id: string; steamRecommendationId: string; reviewText: string; language: string; votedUp: boolean; votesUp: number; playtimeMinutes: number; createdAtSteam: string; updatedAtSteam: string; analysis: Analysis | null };
export type ReviewPage = { items: Review[]; total: number; page: number; size: number };
function record(value: unknown): Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) throw new Error("Invalid response from backend. Retry shortly.");
  return value as Record<string, unknown>;
}
function string(value: unknown): string { if (typeof value !== "string") throw new Error("Invalid text in backend response."); return value; }
function number(value: unknown): number { if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) throw new Error("Invalid count in backend response."); return value; }
function date(value: unknown): string { const text = string(value); if (!Number.isFinite(Date.parse(text))) throw new Error("Invalid date in backend response."); return text; }
export function parseGame(value: unknown): Game {
  const data = record(value);
  return { id: string(data.id), steamAppId: number(data.steamAppId), name: string(data.name), headerImageUrl: data.headerImageUrl === null ? null : string(data.headerImageUrl), createdAt: date(data.createdAt) };
}
export function parseGames(value: unknown): Game[] { if (!Array.isArray(value)) throw new Error("Invalid game list."); return value.map(parseGame); }
export function parseRun(value: unknown): Run | null {
  if (value === null) return null;
  const data = record(value);
  const status = string(data.status);
  if (!["RUNNING", "COMPLETED", "PARTIAL", "FAILED"].includes(status)) throw new Error("Unknown import status.");
  return { id: string(data.id), status: status as Run["status"], fetched: number(data.fetched), inserted: number(data.inserted), updated: number(data.updated), startedAt: date(data.startedAt), finishedAt: data.finishedAt === null ? null : date(data.finishedAt), error: data.error === null ? null : string(data.error) };
}
export function parseReviews(value: unknown): ReviewPage {
  const data = record(value);
  if (!Array.isArray(data.items)) throw new Error("Invalid review list.");
  return { total: number(data.total), page: number(data.page), size: number(data.size), items: data.items.map(value => {
    const review = record(value);
    if (typeof review.votedUp !== "boolean") throw new Error("Invalid review vote.");
    return { id: string(review.id), steamRecommendationId: string(review.steamRecommendationId), reviewText: string(review.reviewText), language: string(review.language), votedUp: review.votedUp, votesUp: number(review.votesUp), playtimeMinutes: number(review.playtimeMinutes), createdAtSteam: date(review.createdAtSteam), updatedAtSteam: date(review.updatedAtSteam), analysis: parseAnalysis(review.analysis) };
  }) };
}
export async function api<T>(path: string, parse: (value: unknown) => T, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  headers.set("Content-Type", "application/json");
  if (init?.method && !["GET", "HEAD"].includes(init.method.toUpperCase())) {
    const sessionResponse = await fetch("/api/auth/session", { cache: "no-store", signal: init.signal });
    if (!sessionResponse.ok) throw new Error("Could not verify your session. Reload and retry.");
    const session = record(await sessionResponse.json());
    if (session.enabled === true) headers.set("X-CSRF-TOKEN", string(session.csrfToken));
    else if (session.enabled !== false) throw new Error("Invalid session response.");
  }
  const response = await fetch(`/api/${path}`, { ...init, headers, cache: "no-store" });
  const value: unknown = response.status === 204 ? null : await response.json();
  if (!response.ok) {
    const error = record(value);
    throw new Error(`${typeof error.message === "string" ? error.message : "Request failed."}${typeof error.requestId === "string" ? ` Reference: ${error.requestId}` : ""}`);
  }
  return parse(value);
}
export function message(error: unknown): string { return error instanceof Error ? error.message : "Request failed. Please retry."; }
