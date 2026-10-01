import { AppShell } from "./app-shell";
import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = { title: "PlayerSignal · Local alpha", description: "Evidence-first Steam review intelligence." };
export default function RootLayout({ children }: Readonly<{children: React.ReactNode}>) {
  return <html lang="en"><body><a className="skip-link" href="#main">Skip to content</a><AppShell>{children}</AppShell></body></html>;
}
