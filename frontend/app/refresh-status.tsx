"use client";
import { useTransition } from "react";
import { useRouter } from "next/navigation";
export function RefreshStatus() {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  return <button disabled={pending} aria-busy={pending} onClick={() => startTransition(() => router.refresh())}>{pending ? "Checking status…" : "Refresh status"}</button>;
}
