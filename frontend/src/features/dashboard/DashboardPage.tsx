import { useTranslation } from 'react-i18next';
import { useAuth } from '../../lib/auth';
import { useCycle } from '../../lib/cycle';
import { PageHeader } from '../../components/PageHeader';
import { StatusTag } from '../../components/StatusTag';

export function DashboardPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const { selected } = useCycle();

  return (
    <div>
      <PageHeader title={t('dashboard.welcome', { name: user?.fullName })} subtitle={t('dashboard.intro')} />
      <div className="grid max-w-2xl gap-4 sm:grid-cols-2">
        <div className="rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
          <div className="text-xs font-semibold uppercase tracking-wide text-neutral-500">
            {t('dashboard.currentCycle')}
          </div>
          <div className="mt-2 flex items-center gap-2">
            <span className="text-lg font-bold text-blue-600">{selected?.code ?? '—'}</span>
            {selected && (
              <StatusTag tone={selected.status === 'OPEN' ? 'success' : 'neutral'}>
                {t(`admin.statuses.${selected.status}`)}
              </StatusTag>
            )}
          </div>
        </div>
        <div className="rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
          <div className="text-xs font-semibold uppercase tracking-wide text-neutral-500">
            {t('dashboard.yourRoles')}
          </div>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {user?.roles.map((role) => (
              <StatusTag key={role} tone="gold">
                {t(`admin.roleNames.${role}`)}
              </StatusTag>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
