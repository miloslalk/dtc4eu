/**
 * Cycle labels (Simone's feedback #1). A cycle's global label is its code only
 * ("2025-26"); the ordinal is per facility — "1st 2025-26" means the facility's
 * first cycle in the programme — and is shown only in the facility history.
 * Dependency-free so `npm test` can run it under plain Node.
 */

/** English ordinal ("1st", "12th", "23rd"); null for missing or non-positive input. */
export function ordinal(n: number | null | undefined): string | null {
  if (n == null || !Number.isInteger(n) || n < 1) return null;
  const suffix = n % 100 >= 11 && n % 100 <= 13 ? 'th' : ['th', 'st', 'nd', 'rd'][n % 10 > 3 ? 0 : n % 10];
  return `${n}${suffix}`;
}

/** "2025-2026" → "2025-26"; other codes pass through. */
export function shortCycleCode(code: string): string {
  return code.replace(/^(\d{4})-\d{2}(\d{2})$/, '$1-$2');
}

/** "1st 2025-26" for a facility's n-th cycle; the bare span when the ordinal is unknown. */
export function facilityCycleLabel(code: string, n: number | null | undefined): string {
  const nth = ordinal(n);
  return nth ? `${nth} ${shortCycleCode(code)}` : shortCycleCode(code);
}
