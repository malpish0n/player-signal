export type BackendHealth = "up" | "unavailable";
export async function getBackendHealth(): Promise<BackendHealth> {
  try {
    const response = await fetch(`${process.env.BACKEND_URL ?? "http://localhost:8080"}/actuator/health/readiness`, {
      cache: "no-store", signal: AbortSignal.timeout(3000),
    });
    if (!response.ok) return "unavailable";
    const body: unknown = await response.json();
    return typeof body === "object" && body !== null && "status" in body && body.status === "UP" ? "up" : "unavailable";
  } catch { return "unavailable"; }
}
