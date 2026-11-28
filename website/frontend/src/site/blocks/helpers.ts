/** Typed accessors over the untyped block payload coming from the CMS. */

export function str(payload: Record<string, unknown>, key: string): string {
  const value = payload[key];
  return typeof value === 'string' ? value : '';
}

export function num(payload: Record<string, unknown>, key: string): number | null {
  const value = payload[key];
  return typeof value === 'number' ? value : null;
}

export function items(payload: Record<string, unknown>, key = 'items'): Record<string, unknown>[] {
  const value = payload[key];
  return Array.isArray(value) ? (value.filter((v) => v && typeof v === 'object') as Record<string, unknown>[]) : [];
}

/**
 * A CMS link target, or '' when it is not a /path, #anchor, http(s) or mailto link.
 * Mirrors the server's SafeUrl so links saved before that check never render as
 * javascript:/data: hrefs.
 */
export function safeHref(url: string): string {
  const trimmed = url.trim();
  return /^(?:https?:\/\/|mailto:|#|\/(?![/\\]))/i.test(trimmed) ? trimmed : '';
}
