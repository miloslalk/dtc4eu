import { useTranslation } from 'react-i18next';
import { useAuth } from '../../lib/auth';
import { useCycle } from '../../lib/cycle';
import { PageHeader } from '../../components/PageHeader';
import { StatusTag } from '../../components/StatusTag';
import { Spinner } from '../../components/Spinner';
import { cycleLabel } from '../../lib/types';
import { useMyProgress, type ProgressTask } from '../progress/api';
import { ProgressMatrix } from '../progress/ProgressMatrix';

const FACILITATOR_TASKS: ProgressTask[] = ['GAS_SET', 'QCNC_INTERNAL_FACILITATOR', 'DTCRO_LONG_FORM'];
const COORDINATOR_TASKS: ProgressTask[] = [
  'QCNC_TERNA_COORDINATOR_OWN', 'QCNC_TERNA_COORDINATOR_DELEGATION', 'DTCRO_DELEGATION', 'QUALITY_DOCUMENTS',
  'PARTICIPATION_CHECKLIST',
];

export function DashboardPage() {
  const { t } = useTranslation();
  const { user, hasAnyRole } = useAuth();
  const { selected } = useCycle();
  const overview = hasAnyRole('ADMIN', 'SUPER_ADMIN', 'UPPER_COORDINATOR', 'RESEARCH_TEAM');
  const coordinator = hasAnyRole('COORDINATOR');
  const facilitator = hasAnyRole('INTERNAL_FACILITATOR');
  const showTasks = overview || coordinator || facilitator;
  const progress = useMyProgress(showTasks && selected !== null);
  // one list per role; the overview shows every instrument across the cycle's ternas
  const tasks: ProgressTask[] = overview || (coordinator && facilitator)
    ? [...FACILITATOR_TASKS, ...COORDINATOR_TASKS]
    : coordinator ? COORDINATOR_TASKS : FACILITATOR_TASKS;

  return (
    <div>
      <PageHeader title={t('dashboard.welcome', { name: user?.fullName })} subtitle={t('dashboard.intro')} />
      <div className="grid max-w-2xl gap-4 sm:grid-cols-2">
        <div className="rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
          <div className="text-xs font-semibold uppercase tracking-wide text-neutral-500">
            {t('dashboard.currentCycle')}
          </div>
          <div className="mt-2 flex items-center gap-2">
            <span className="text-lg font-bold text-blue-600">{selected ? cycleLabel(selected) : '—'}</span>
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

      {showTasks && selected && (
        <section className="mt-8">
          <h2 className="mb-1 text-sm font-bold uppercase tracking-wide text-blue-600">
            {overview ? t('progress.overviewTitle', { cycle: cycleLabel(selected) })
              : t('progress.todoTitle', { cycle: cycleLabel(selected) })}
          </h2>
          <p className="mb-3 text-xs text-neutral-500">{t('progress.legend')}</p>
          {progress.isLoading ? <Spinner label={t('common.loading')} />
            : (progress.data ?? []).length === 0
              ? <p className="text-sm text-neutral-500">{t('progress.nothing', { cycle: cycleLabel(selected) })}</p>
              : <ProgressMatrix rows={progress.data ?? []} tasks={tasks} />}
        </section>
      )}
    </div>
  );
}
