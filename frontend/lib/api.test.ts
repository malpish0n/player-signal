import { afterEach, expect, it, vi } from "vitest";
import { api, parseGames, parseReviews, parseRun } from "./api";
afterEach(() => vi.unstubAllGlobals());
it("distinguishes an import that has not run from partial results", () => {
  expect(parseRun(null)).toBeNull();
  expect(parseRun({id:"run", status:"PARTIAL", fetched:100, inserted:90, updated:2, startedAt:"2026-10-01T00:00:00Z", finishedAt:"2026-10-01T00:00:10Z", error:null})?.status).toBe("PARTIAL");
});
it("rejects invented statuses and malformed counts", () => {
  expect(() => parseRun({status:"READY"})).toThrow("Unknown import status");
  expect(() => parseReviews({items:[], total:-1, page:0, size:20})).toThrow();
  expect(() => parseGames({items:[]})).toThrow();
});
it("preserves original review text without interpreting markup", () => {
  const text = " <script>alert(1)</script>\n  exact text ";
  const page = parseReviews({total:1, page:0, size:20, items:[{id:"id", steamRecommendationId:"42", reviewText:text, language:"english", votedUp:false, votesUp:0, playtimeMinutes:42, createdAtSteam:"2026-10-01T00:00:00Z", updatedAtSteam:"2026-10-01T00:00:00Z"}]});
  expect(page.items[0].reviewText).toBe(text);
});
it("exposes backend errors and their diagnostic request ID", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({message:"Steam unavailable", requestId:"request-42"}, {status:502})));
  await expect(api("games", parseGames)).rejects.toThrow("Steam unavailable Reference: request-42");
});
