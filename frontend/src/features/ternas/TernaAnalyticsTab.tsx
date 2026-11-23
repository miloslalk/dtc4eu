import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Spinner } from '../../components/Spinner';
import { ClassificationBadge } from '../../components/ClassificationBadge';
import { apiErrorMessage, downloadFile } from '../../lib/api';
import { Button } from '../../components/Button';
import { useQuestionnaires } from '../questionnaires/api';
import { useTernaBenchmarks, useTernaPopulation } from '../scoring/api';
import { MEMBER_CATEGORIES } from '../facilities/api';

/** Terna analytics (v0.2 register §3.4 + §3.6): benchmark strength areas and the TAB.2 population table. */
export function TernaAnalyticsTab({ ternaId }: { ternaId: number }) {
  const { t } = useTranslation();
  const questionnaires = useQuestionnaires();
  const scorable = useMemo(
    () => (questionnaires.data ?? []).filter((q) => q.active && q.formCount > 0),
    [questionnaires.data],
  );
  const [questionnaireId, setQuestionnaireId] = useState<number | null>(null);
  const effectiveId = questionnaireId ?? scorable[0]?.id ?? null;

  const benchmarks = useTernaBenchmarks(ternaId, effectiveId);
  const population = useTernaPopulation(ternaId, effectiveId);

  if (questionnaires.isLoading || benchmarks.isLoading || population.isLoading) {
    return <Spinner label={t('common.loading')} />;
  }
  if (benchmarks.error) {
    return <p className="rounded-lg bg-error-surface px-3 py-2 text-sm text-error">{apiErrorMessage(benchmarks.error)}</p>;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        {scorable.length > 1 ? (
          <select
            className="rounded-lg border border-neutral-300 px-3 py-1.5 text-sm"
            value={effectiveId ?? ''}
            onChange={(e) => setQuestionnaireId(Number(e.target.value))}
          >
            {scorable.map((q) => <option key={q.id} value={q.id}>{q.code}</option>)}
          </select>
        ) : <span />}
        {effectiveId !== null && (
          <Button variant="ghost" onClick={() => downloadFile(`/ternas/${ternaId}/analytics/export.xlsx`,
            { questionnaireId: effectiveId }, `terna-${ternaId}-report.xlsx`)}>
            {t('ternas.analytics.exportXlsx')}
          </Button>
        )}
      </div>

      <section className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
        <header className="border-b border-neutral-200 bg-blue-50 px-5 py-3">
          <h2 className="text-sm font-bold text-blue-700">{t('ternas.analytics.benchmarks')}</h2>
          <p className="mt-0.5 text-xs text-neutral-500">{t('ternas.analytics.benchmarksHint')}</p>
        </header>
        {(benchmarks.data ?? []).length === 0 ? (
          <p className="px-5 py-4 text-sm text-neutral-400">{t('scoring.noData')}</p>
        ) : (
          <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-200 text-left text-xs font-semibold text-neutral-500">
                <th className="px-4 py-2">{t('ternas.analytics.area')}</th>
                <th className="px-4 py-2">{t('ternas.analytics.bestFacility')}</th>
                <th className="px-4 py-2 text-right">{t('ternas.analytics.bestScore')}</th>
                <th className="px-4 py-2">{t('scoring.classification')}</th>
              </tr>
            </thead>
            <tbody>
              {(benchmarks.data ?? []).map((row) => (
                <tr key={row.areaNo ?? row.areaCode ?? Math.random()} className="border-b border-neutral-100 last:border-0">
                  <td className="px-4 py-2">
                    <span className="mr-1.5 font-mono text-xs font-bold text-blue-600">{row.areaCode}</span>
                    {row.areaName}
                  </td>
                  <td className="px-4 py-2 font-mono font-semibold">{row.bestFacilityCode ?? '—'}</td>
                  <td className="px-4 py-2 text-right font-mono font-bold text-blue-700">{row.bestScore ?? '—'}</td>
                  <td className="px-4 py-2"><ClassificationBadge classification={row.classification} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </section>

      {population.data && (
        <section className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
          <header className="border-b border-neutral-200 bg-blue-50 px-5 py-3">
            <h2 className="text-sm font-bold text-blue-700">{t('ternas.analytics.population')}</h2>
            <p className="mt-0.5 text-xs text-neutral-500">{t('ternas.analytics.populationHint')}</p>
          </header>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-200 text-left text-xs font-semibold text-neutral-500">
                  <th className="px-4 py-2">{t('ternas.analytics.facility')}</th>
                  {MEMBER_CATEGORIES.map((category) => (
                    <th key={category} colSpan={2} className="border-l border-neutral-100 px-2 py-2 text-center">
                      {t(`facilities.categories.${category}`)}
                    </th>
                  ))}
                  <th colSpan={2} className="border-l border-neutral-100 px-2 py-2 text-center">
                    {t('ternas.analytics.total')}
                  </th>
                </tr>
                <tr className="border-b border-neutral-200 text-left text-[10px] text-neutral-400">
                  <th className="px-4 py-1" />
                  {[...MEMBER_CATEGORIES, 'TOTAL'].map((category) => (
                    <ClusterSubheader key={category} />
                  ))}
                </tr>
              </thead>
              <tbody>
                {[...population.data.facilities, population.data.totals].map((row, index) => {
                  const isTotal = row.facilityId === null;
                  return (
                    <tr key={row.facilityId ?? `total-${index}`}
                      className={`border-b border-neutral-100 last:border-0 ${isTotal ? 'bg-blue-50/60 font-semibold' : ''}`}>
                      <td className="px-4 py-2 font-mono">
                        {isTotal ? t('ternas.analytics.total') : row.facilityCode}
                      </td>
                      {MEMBER_CATEGORIES.map((category) => {
                        const cell = row.byCategory[category] ?? { collected: 0, roster: 0 };
                        return (
                          <CellPair key={category} collected={cell.collected} roster={cell.roster} />
                        );
                      })}
                      <CellPair collected={row.collectedTotal} roster={row.rosterTotal} />
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}

function ClusterSubheader() {
  const { t } = useTranslation();
  return (
    <>
      <th className="border-l border-neutral-100 px-2 py-1 text-right">{t('ternas.analytics.collected')}</th>
      <th className="px-2 py-1 text-right">{t('ternas.analytics.roster')}</th>
    </>
  );
}

function CellPair({ collected, roster }: { collected: number; roster: number }) {
  return (
    <>
      <td className="border-l border-neutral-100 px-2 py-2 text-right font-mono">{collected}</td>
      <td className="px-2 py-2 text-right font-mono text-neutral-500">{roster}</td>
    </>
  );
}
