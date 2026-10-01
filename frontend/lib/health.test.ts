import { afterEach, expect, it, vi } from "vitest";
import { getBackendHealth } from "./health";
afterEach(() => vi.unstubAllGlobals());
it("accepts only a successful UP readiness response", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ status: "UP" })));
  expect(await getBackendHealth()).toBe("up");
});
it.each([Response.json({status:"DOWN"}, {status:503}), Response.json({}), new Response("invalid")])("rejects unhealthy or malformed responses", async (response) => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response));
  expect(await getBackendHealth()).toBe("unavailable");
});
it("keeps the page available when the backend cannot be reached", async () => {
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
  expect(await getBackendHealth()).toBe("unavailable");
});
