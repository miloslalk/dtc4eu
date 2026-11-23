import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { ClassificationBadge } from '../../components/ClassificationBadge';
import { useAuth } from '../../lib/auth';
import { apiErrorMessage } from '../../lib/api';
import { useQuestionnaires } from '../questionnaires/api';
import { useQcncBoard, type QcncMetaArea } from '../facilities/api';
import {
  useFacilityScores, useRecomputeScores, useScoreSummary,
  type AreaScoreRow, type ItemScoreRow, type SummaryTable,
} from './api';

/**
 * Facility computed-scores tab (evolved S-33, v0.2 register §3): the
 * Quant-SAWA and Quant-SPWP grids with the full aggregation columns of the
 * Terna_Example1 workbook, classification badges on every score, the
 * strength/weakness point lists and the per-cycle Summary Table.
 */
export function FacilityScoresTab({ facilityId }: { facilityId: number }) {
  const { t } = useTranslation();
  const { hasAnyRole } = useAuth();
  const questionnaires = useQuestionnaires();
  const scorable = useMemo(
    () => (questionnaires.data ?? []).filter((q) => q.active && q.formCount > 0),
    [questionnaires.data],
  );
  const [questionnaireId, setQuestionnaireId] = useState<number | null>(null);
  const effectiveId = questionnaireId ?? scorable[0]?.id ?? null;

  const { data: scores, isLoading, error } = useFacilityScores(facilityId, effectiveId);
  const { data: summary } = useScoreSummary(facilityId, effectiveId);
  const { data: qcnc } = useQcncBoard(facilityId);
  const isDtcro = scorable.find((q) => q.id === effectiveId)?.code.startsWith('DTCRO') ?? false;
  const recompute = useRecomputeScores(facilityId);
  const canRecompute = hasAnyRole('ADMIN', 'SUPER_ADMIN', 'COORDINATOR');

  if (questionnaires.isLoading || isLoading) return <Spinner label={t('common.loading')} />;
  if (error) {
    return <p className="rounded-lg bg-error-surface px-3 py-2 text-sm text-error">{apiErrorMessage(error)}</p>;
  }

  const hasData = scores != null && scores.computedAt != null;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          {scorable.length > 1 && (
            <select
              className="rounded-lg border border-neutral-300 px-3 py-1.5 text-sm"
              value={effectiveId ?? ''}
              onChange={(e) => setQuestionnaireId(Number(e.target.value))}
            >
              {scorable.map((q) => <option key={q.id} value={q.id}>{q.code}</option>)}
            </select>
          )}
          {hasData && (
            <span className="text-xs text-neutral-500">
              {t('scoring.computedAt', { when: scores.computedAt?.slice(0, 16).replace('T', ' ') })}
            </span>
          )}
        </div>
        {canRecompute && effectiveId !== null && (
          <Button variant="ghost" loading={recompute.isPending}
            onClick={() => recompute.mutate(effectiveId, { onError: (e) => alert(apiErrorMessage(e)) })}>
            {t('scoring.recompute')}
          </Button>
        )}
      </div>

      {!hasData && (
        <p className="rounded-xl border border-neutral-200 bg-white px-4 py-6 text-center text-sm text-neutral-500">
          {t('scoring.noData')}
        </p>
      )}

      {hasData && (
        <>
          <section className="flex flex-wrap items-center gap-4 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-neutral-500">{t('scoring.overall')}</p>
              <p className="font-mono text-3xl font-bold text-blue-700">{scores.overallScore ?? '—'}</p>
            </div>
            <ClassificationBadge classification={scores.overallClassification} />
          </section>

          <ScoreGrid title={t('scoring.areaGrid')} rows={scores.areas}
            firstColumn={{
              header: t('scoring.area'),
              render: (row: AreaScoreRow) => (
                <span>
                  <span className="mr-1.5 font-mono text-xs font-bold text-blue-600">{row.areaCode}</span>
                  {row.areaName}
                </span>
              ),
            }} />

          <ScoreGrid title={t('scoring.itemGrid')} rows={scores.items}
            firstColumn={{
              header: t('scoring.coordinate'),
              render: (row: ItemScoreRow) => <span className="font-mono font-bold">{row.coordinate}</span>,
            }} />

          <section className="grid gap-4 sm:grid-cols-2">
            <PointList title={t('scoring.strengthPoints')} tone="success"
              points={[...scores.strengthAreas.map((a) => `${t('scoring.area')} ${a}`), ...scores.strengthPoints]} />
            <PointList title={t('scoring.weaknessPoints')} tone="error"
              points={[...scores.weaknessAreas.map((a) => `${t('scoring.area')} ${a}`), ...scores.weaknessPoints]} />
          </section>

          {summary && <SummaryCard summary={isDtcro ? withQualitativeAreas(summary, qcnc?.metaAreas) : summary} />}
        </>
      )}
    </div>
  );
}

const NUMERIC_COLUMNS = ['userMean', 'familyMean', 'staffMean', 'managerMean', 'averI', 'comValue', 'delValue'] as const;
const COLUMN_KEYS = { userMean: 'user', familyMean: 'family', staffMean: 'staff', managerMean: 'manager', averI: 'averI', comValue: 'com', delValue: 'del' } as const;

function ScoreGrid<T extends AreaScoreRow | ItemScoreRow>({ title, rows, firstColumn }: {
  title: string;
  rows: T[];
  firstColumn: { header: string; render: (row: T) => React.ReactNode };
}) {
  const { t } = useTranslation();
  return (
    <section className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
      <header className="border-b border-neutral-200 bg-blue-50 px-5 py-3">
        <h2 className="text-sm font-bold text-blue-700">{title}</h2>
      </header>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 text-left text-xs font-semibold text-neutral-500">
              <th className="px-4 py-2">{firstColumn.header}</th>
              {NUMERIC_COLUMNS.map((key) => (
                <th key={key} className="px-2 py-2 text-right">{t(`scoring.${COLUMN_KEYS[key]}`)}</th>
              ))}
              <th className="px-2 py-2 text-right">{t('scoring.final')}</th>
              <th className="px-4 py-2">{t('scoring.classification')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={index} className="border-b border-neutral-100 last:border-0 odd:bg-blue-50/40">
                <td className="max-w-[18rem] px-4 py-1.5">{firstColumn.render(row)}</td>
                {NUMERIC_COLUMNS.map((key) => (
                  <td key={key} className="px-2 py-1.5 text-right font-mono text-xs text-neutral-600">
                    {row[key] ?? '—'}
                  </td>
                ))}
                <td className="px-2 py-1.5 text-right font-mono text-xs font-bold text-blue-700">
                  {row.finalScore ?? '—'}
                </td>
                <td className="px-4 py-1.5"><ClassificationBadge classification={row.classification} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function PointList({ title, points, tone }: { title: string; points: string[]; tone: 'success' | 'error' }) {
  return (
    <section className="rounded-xl border border-neutral-200 bg-white p-4 shadow-sm">
      <h3 className={`mb-2 text-xs font-bold uppercase tracking-wide ${
        tone === 'success' ? 'text-success' : 'text-error'
      }`}>
        {title}
      </h3>
      {points.length === 0 ? (
        <p className="text-sm text-neutral-400">—</p>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {points.map((point) => (
            <span key={point} className={`rounded px-2 py-0.5 font-mono text-xs font-semibold ${
              tone === 'success' ? 'bg-success-surface text-success' : 'bg-error-surface text-error'
            }`}>
              {point}
            </span>
          ))}
        </div>
      )}
    </section>
  );
}

/**
 * Spec §6.5: the DTCRO areas of the QCNC meta-areas that are strong (weak) in
 * all three administrations are the qualitative strength (weakness) areas.
 * Stays null ("not available") while any meta-area lacks an administration.
 */
function withQualitativeAreas(summary: SummaryTable, metaAreas: QcncMetaArea[] | undefined): SummaryTable {
  if (!metaAreas || metaAreas.length === 0 || metaAreas.some((m) => m.status === 'INCOMPLETE')) return summary;
  const areasWith = (status: QcncMetaArea['status']) =>
    metaAreas.filter((m) => m.status === status).flatMap((m) => m.dtcroAreas).map(String);
  return { ...summary, qualStrengthAreas: areasWith('STRENGTH'), qualWeaknessAreas: areasWith('WEAKNESS') };
}

function SummaryCard({ summary }: { summary: SummaryTable }) {
  const { t } = useTranslation();
  const rows: { key: string; values: string[] | null }[] = [
    { key: 'qualWeaknessAreas', values: summary.qualWeaknessAreas },
    { key: 'qualWeaknessPoints', values: summary.qualWeaknessPoints },
    { key: 'quantWeaknessAreas', values: summary.quantWeaknessAreas },
    { key: 'quantWeaknessPoints', values: summary.quantWeaknessPoints },
    { key: 'qualStrengthAreas', values: summary.qualStrengthAreas },
    { key: 'qualStrengthPoints', values: summary.qualStrengthPoints },
    { key: 'quantStrengthAreas', values: summary.quantStrengthAreas },
    { key: 'quantStrengthPoints', values: summary.quantStrengthPoints },
    { key: 'benchmarkAreas', values: summary.benchmarkAreas },
    { key: 'excellencePoints', values: summary.excellencePoints },
    { key: 'epBench', values: summary.epBench },
  ];
  return (
    <section className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
      <header className="border-b border-neutral-200 bg-blue-50 px-5 py-3">
        <h2 className="text-sm font-bold text-blue-700">{t('scoring.summary.title')}</h2>
        <p className="mt-0.5 text-xs text-neutral-500">{t('scoring.summary.hint')}</p>
      </header>
      <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <tbody>
          {rows.map((row) => (
            <tr key={row.key} className="border-b border-neutral-100 last:border-0">
              <td className="min-w-[9rem] px-4 py-1.5 text-xs font-semibold text-neutral-600 sm:w-72">
                {t(`scoring.summary.${row.key}`)}
              </td>
              <td className="px-4 py-1.5 font-mono text-xs text-neutral-800">
                {row.values === null ? <span className="text-neutral-300">—</span>
                  : row.values.length === 0 ? '—' : row.values.join('; ')}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      </div>
    </section>
  );
}
