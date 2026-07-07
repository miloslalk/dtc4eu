import type { ReactNode } from 'react';

export type Tone = 'gold' | 'success' | 'info' | 'warning' | 'error' | 'neutral';

const tones: Record<Tone, string> = {
  gold: 'bg-gold-50 text-gold-900 border-gold-300',
  success: 'bg-success-surface text-success border-success/30',
  info: 'bg-info-surface text-info border-info/30',
  warning: 'bg-warning-surface text-warning border-warning/30',
  error: 'bg-error-surface text-error border-error/30',
  neutral: 'bg-neutral-100 text-neutral-600 border-neutral-300',
};

export function StatusTag({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${tones[tone]}`}>
      {children}
    </span>
  );
}
