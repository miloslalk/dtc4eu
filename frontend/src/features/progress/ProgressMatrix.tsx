import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { StatusTag, type Tone } from '../../components/StatusTag';
import { taskLink, type FacilityProgress, type ProgressTask, type TaskStatus } from './api';

const TONE: Record<TaskStatus, Tone> = { NOT_STARTED: 'neutral', IN_PROGRESS: 'warning', DONE: 'success' };

/** Facilities × instruments with a status and a link per cell (dashboard and terna detail). */
export function ProgressMatrix({ rows, tasks }: { rows: FacilityProgress[]; tasks: ProgressTask[] }) {
  const { t } = useTranslation();
  return (
    <div className="overflow-x-auto rounded-xl border border-neutral-200 bg-white shadow-sm">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-neutral-200 text-left text-xs font-semibold text-neutral-500">
            <th className="px-3 py-2">{t('facilities.title')}</th>
            {tasks.map((task) => <th key={task} className="px-3 py-2">{t(`progress.tasks.${task}`)}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.facilityId} className="border-b border-neutral-100 last:border-0">
              <td className="px-3 py-2 align-top">
                <Link to={`/facilities/${row.facilityId}`} className="font-semibold text-azure-500 hover:underline">
                  {row.facilityCode}
                </Link>
                <div className="text-xs text-neutral-500">
                  {row.ternaCode ?? '—'} · {t('facilities.internalFacilitator')}: {row.internalFacilitatorName ?? '—'}
                </div>
              </td>
              {tasks.map((task) => {
                const cell = row.tasks.find((x) => x.task === task);
                if (!cell) return <td key={task} className="px-3 py-2">—</td>;
                return (
                  <td key={task} className="px-3 py-2 align-top">
                    <Link to={taskLink(row, task)} className="hover:opacity-80">
                      <StatusTag tone={TONE[cell.status]}>{t(`progress.status.${cell.status}`)}</StatusTag>
                    </Link>
                    {cell.count > 0 && <span className="ml-1 text-xs text-neutral-500">×{cell.count}</span>}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
