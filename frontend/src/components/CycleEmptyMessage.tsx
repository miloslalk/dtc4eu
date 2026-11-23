import { useTranslation } from 'react-i18next';
import { useCycle } from '../lib/cycle';
import { cycleLabel } from '../lib/types';

/**
 * Empty-list text that names the selected cycle and offers the switch
 * (Simone's feedback #1: "no ternas" looked like lost data when the data
 * simply lives in another cycle).
 */
export function CycleEmptyMessage({ what }: { what: string }) {
  const { t } = useTranslation();
  const { cycles, selected, select } = useCycle();
  if (!selected) return <>{t('common.noResults')}</>;
  const others = cycles.filter((c) => c.id !== selected.id);
  return (
    <span className="inline-flex flex-col items-center gap-2">
      <span>{t('cycle.emptyIn', { what, cycle: cycleLabel(selected) })}</span>
      {others.length > 0 && (
        <label className="inline-flex items-center gap-2 text-xs text-neutral-600">
          {t('cycle.switchTo')}
          <select className="rounded-md border border-neutral-300 px-2 py-1 text-sm" value=""
            onChange={(e) => e.target.value && select(Number(e.target.value))}>
            <option value="">—</option>
            {others.map((c) => (
              <option key={c.id} value={c.id}>{cycleLabel(c)} · {t(`admin.statuses.${c.status}`)}</option>
            ))}
          </select>
        </label>
      )}
    </span>
  );
}
