// Next's internal request URL can use the container hostname. Compare the browser
// Origin with the actual Host header, not the internal upstream URL.
export function sameOrigin(origin: string | null, host: string | null): boolean {
  if (!origin || !host) return false;
  try {
    const url = new URL(origin);
    return (url.protocol === "http:" || url.protocol === "https:") && url.origin === origin && url.host === host;
  } catch { return false; }
}
