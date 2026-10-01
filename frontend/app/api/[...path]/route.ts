import { sameOrigin } from "@/lib/same-origin";
import { NextRequest } from "next/server";

const allowedPath = /^(?:workspace(?:\/(?:operations|export|account\/delete|switch|invites(?:\/(?:accept|[0-9a-f-]{36}\/revoke))?|members\/[0-9a-f-]{36}\/(?:role|remove)))?|auth\/(?:session|login|register|logout)|(?:analysis|issues|overview)\/demo|games(?:\/preview|\/[0-9a-f-]{36}(?:\/overview|\/comparison|\/automation|\/alerts(?:\/check)?|\/notifications(?:\/read)?|\/delete|\/saved-views(?:\/[0-9a-f-]{36}\/remove)?|\/reports(?:\/(?:generate|[0-9a-f-]{36}))?|\/usage|\/updates(?:\/[0-9a-f-]{36})?|\/issues(?:\/(?:rebuild|[0-9a-f-]{36}(?:\/(?:trend|workflow))?))?|\/analysis|\/reviews(?:\/export|\/[0-9a-f-]{36}\/analyses)?|\/sync(?:\/latest)?)?)?)$/;
async function forward(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const resource = path.join("/");
  if (!allowedPath.test(resource)) return Response.json({ message: "Unknown API route." }, { status: 404 });
  if (request.method === "POST" && !sameOrigin(request.headers.get("origin"), request.headers.get("host"))) {
    return Response.json({ message: "Cross-origin writes are not allowed." }, { status: 403 });
  }
  try {
    const body = request.method === "POST" ? await request.text() : undefined;
    if (body && body.length > 4096) return Response.json({ message: "Request is too large." }, { status: 413 });
    const backend = process.env.BACKEND_URL ?? "http://localhost:8080";
    const upstreamHeaders = new Headers({ "Content-Type": "application/json" });
    const session = request.cookies.get("PLAYERSIGNAL_SESSION");
    if (session) upstreamHeaders.set("Cookie", `PLAYERSIGNAL_SESSION=${session.value}`);
    const csrf = request.headers.get("X-CSRF-TOKEN");
    if (csrf) upstreamHeaders.set("X-CSRF-TOKEN", csrf);
    const response = await fetch(`${backend}/api/${resource}${request.nextUrl.search}`, {
      method: request.method,
      headers: upstreamHeaders,
      body: body || undefined,
      cache: "no-store",
      signal: AbortSignal.timeout(55000),
    });
    const csv = resource.endsWith('/reviews/export') && response.ok;
    const responseHeaders = new Headers({ "Content-Type": csv ? "text/csv; charset=UTF-8" : "application/json", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" });
    if (csv) responseHeaders.set("Content-Disposition", `attachment; filename="playersignal-${path[1]}-reviews.csv"`);
    for (const cookie of response.headers.getSetCookie()) {
      if (cookie.startsWith("PLAYERSIGNAL_SESSION=")) responseHeaders.append("Set-Cookie", cookie);
    }
    return new Response(response.status === 204 ? null : await response.arrayBuffer(), {
      status: response.status,
      headers: responseHeaders,
    });
  } catch {
    return Response.json({ message: "Backend unavailable. Previously imported reviews are preserved. Retry shortly." }, { status: 502 });
  }
}
export const GET = forward;
export const POST = forward;
