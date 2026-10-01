import { expect, it } from "vitest";
import { sameOrigin } from "./same-origin";
it("allows the browser host even when Next uses an internal container URL", () => {
  expect(sameOrigin("http://localhost:3000", "localhost:3000")).toBe(true);
});
it.each(["https://evil.test", "http://localhost:3001", "null", "http://user@localhost:3000", null])("rejects foreign or opaque origins: %s", origin => {
  expect(sameOrigin(origin, "localhost:3000")).toBe(false);
});
