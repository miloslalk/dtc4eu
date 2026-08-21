import { useTranslation } from 'react-i18next';
import { StatusTag } from './StatusTag';
import type { Classification } from '../features/scoring/api';

const TONES = { CRITICAL: 'error', INTERMEDIATE: 'gold', EFFICACY: 'success' } as const;

/** TAB.5 effectiveness badge, required next to every item and area score (v0.2 register §3.3). */
export function ClassificationBadge({ classification }: { classification: Classification | null }) {
  const { t } = useTranslation();
  if (!classification) return <span className="text-neutral-300">—</span>;
  return <StatusTag tone={TONES[classification]}>{t(`scoring.classifications.${classification}`)}</StatusTag>;
}
