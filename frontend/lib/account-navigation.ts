// Only known workspace pages are valid destinations after authentication.
export function workspaceDestination(value: string | null): string {
  if (!value || /[\\\u0000-\u0020]/.test(value)) return '/';
  try {
    const url = new URL(value, 'https://playersignal.invalid');
    if (!value.startsWith('/') || url.origin !== 'https://playersignal.invalid' || url.hash) return '/';
    if (url.pathname !== '/' && !/^\/games\/[0-9a-f-]{36}(?:\/(?:overview|processing|comparison|issues(?:\/[0-9a-f-]{36})?))?$/.test(url.pathname)) return '/';
    return url.pathname + url.search;
  } catch { return '/'; }
}
export function accountLink(page: 'login' | 'register', destination: string): string {
  return `/${page}?next=${encodeURIComponent(workspaceDestination(destination))}`;
}
export const SESSION_REQUIRED_EVENT = 'playersignal:session-required';
