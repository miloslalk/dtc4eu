import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

/** Horizontal filter strip above list tables; filters combine with AND (spec §4). */
export function FilterBar({ children, onClear }: { children: ReactNode; onClear?: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="mb-4 flex flex-wrap items-center gap-2 rounded-xl border border-neutral-200 bg-white px-3 py-2.5 shadow-sm">
      {children}
      {onClear && (
        <button type="button" onClick={onClear}
          className="ml-auto text-sm font-medium text-azure-500 hover:underline">
          {t('common.clearFilters')}
        </button>
      )}
    </div>
  );
}
