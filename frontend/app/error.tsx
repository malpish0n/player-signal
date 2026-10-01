"use client";
export default function ErrorPage({ reset }: {reset: () => void}) {
  return <section className="panel" role="alert"><h1>Workspace could not load</h1><p>Please try loading this page again.</p><button onClick={reset}>Try again</button></section>;
}
