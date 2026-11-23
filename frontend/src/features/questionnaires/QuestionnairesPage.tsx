import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { TextField } from '../../components/fields';
import { DataTable, type Column } from '../../components/DataTable';
import { StatusTag } from '../../components/StatusTag';
import { useQuery } from '@tanstack/react-query';
import { api, apiErrorMessage } from '../../lib/api';
import { Spinner } from '../../components/Spinner';
import { ProfileForm } from '../../components/ProfileForm';
import type { QcncStandard } from '../facilities/api';
import { useCycle } from '../../lib/cycle';
import { cycleLabel } from '../../lib/types';
import { useFacilities } from '../facilities/api';
import { useCreateQuestionnaire, useQuestionnaires, type ProfileSchema, type QuestionnaireRow } from './api';

export function QuestionnairesPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data, isLoading } = useQuestionnaires();
  const create = useCreateQuestionnaire();

  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ code: '', name: '', scaleMin: '1', scaleMax: '4' });
  const [error, setError] = useState<string | null>(null);

  const columns: Column<QuestionnaireRow>[] = [
    { key: 'code', header: t('questionnaires.code'), render: (q) => <span className="font-semibold">{q.code}</span> },
    { key: 'name', header: t('questionnaires.name'), render: (q) => q.name },
    { key: 'scale', header: t('questionnaires.scale'), render: (q) => `${q.scaleMin}–${q.scaleMax}` },
    { key: 'areas', header: t('questionnaires.areas'), render: (q) => q.areaCount },
    { key: 'items', header: t('questionnaires.items'), render: (q) => q.itemCount },
    { key: 'forms', header: t('questionnaires.forms'), render: (q) => q.formCount },
    {
      key: 'active',
      header: t('common.active'),
      render: (q) => (
        <StatusTag tone={q.active ? 'success' : 'neutral'}>
          {q.active ? t('common.active') : t('common.inactive')}
        </StatusTag>
      ),
    },
  ];

  const handleCreate = async () => {
    if (!form.code.trim() || !form.name.trim()) {
      setError(t('common.required'));
      return;
    }
    try {
      const created = await create.mutateAsync({
        code: form.code.trim().toUpperCase(),
        name: form.name.trim(),
        scaleMin: Number(form.scaleMin),
        scaleMax: Number(form.scaleMax),
      });
      setOpen(false);
      navigate(`/questionnaires/${created.id}`);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <div>
      <PageHeader
        title={t('questionnaires.title')}
        actions={<Button onClick={() => setOpen(true)}>{t('questionnaires.new')}</Button>}
      />

      <DataTable
        columns={columns}
        rows={data ?? []}
        rowKey={(q) => q.id}
        loading={isLoading}
        onRowClick={(q) => navigate(`/questionnaires/${q.id}`)}
      />

      <FacilityInstruments />

      <Modal
        title={t('questionnaires.new')}
        open={open}
        onClose={() => setOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setOpen(false)}>{t('common.cancel')}</Button>
            <Button onClick={handleCreate} loading={create.isPending}>{t('common.create')}</Button>
          </>
        }
      >
        <div className="space-y-4">
          <TextField label={t('questionnaires.code')} required placeholder="MHSRO" value={form.code}
            onChange={(e) => setForm({ ...form, code: e.target.value })} />
          <TextField label={t('questionnaires.name')} required value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <div className="grid gap-3 sm:grid-cols-2">
            <TextField label="Min" type="number" value={form.scaleMin}
              onChange={(e) => setForm({ ...form, scaleMin: e.target.value })} />
            <TextField label="Max" type="number" value={form.scaleMax}
              onChange={(e) => setForm({ ...form, scaleMax: e.target.value })} />
          </div>
          {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
        </div>
      </Modal>
    </div>
  );
}

/**
 * The instruments completed per facility (Simone's feedback #5: he looked for
 * GAS-Set and QCNC here). Each opens the facility's own tab in the selected cycle.
 */
const FACILITY_INSTRUMENTS = [
  { key: 'gasSet', tab: 'sag', preview: true },
  { key: 'qcnc', tab: 'qcnc', preview: true },
  { key: 'qualityDocuments', tab: 'participation', preview: false },
] as const;
type InstrumentKey = (typeof FACILITY_INSTRUMENTS)[number]['key'];

function FacilityInstruments() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { selected } = useCycle();
  const facilities = useFacilities({ page: 0, size: 200 });
  const [chosen, setChosen] = useState<Record<string, string>>({});
  const [previewing, setPreviewing] = useState<InstrumentKey | null>(null);

  return (
    <section className="mt-8">
      <h2 className="mb-1 text-sm font-bold uppercase tracking-wide text-blue-600">
        {t('questionnaires.facilityInstruments.title')}
      </h2>
      <p className="mb-3 text-xs text-neutral-500">
        {t('questionnaires.facilityInstruments.intro', { cycle: selected ? cycleLabel(selected) : '—' })}
      </p>
      <div className="grid gap-3 md:grid-cols-3">
        {FACILITY_INSTRUMENTS.map(({ key, tab, preview }) => (
          <div key={key} className="flex flex-col rounded-xl border border-neutral-200 bg-white p-4 shadow-sm">
            <h3 className="text-sm font-bold text-neutral-800">{t(`questionnaires.facilityInstruments.${key}.name`)}</h3>
            <p className="mt-1 flex-1 text-xs text-neutral-600">{t(`questionnaires.facilityInstruments.${key}.about`)}</p>
            <div className="mt-3 flex gap-2">
              <select aria-label={t('questionnaires.facilityInstruments.chooseFacility')}
                className="min-w-0 flex-1 rounded-lg border border-neutral-300 px-2 py-1.5 text-sm"
                value={chosen[key] ?? ''} onChange={(e) => setChosen({ ...chosen, [key]: e.target.value })}>
                <option value="">{t('questionnaires.facilityInstruments.chooseFacility')}</option>
                {(facilities.data?.content ?? []).map((f) => (
                  <option key={f.id} value={f.id}>{f.facilityCode} — {f.name}</option>
                ))}
              </select>
              <Button variant="ghost" disabled={!chosen[key]}
                onClick={() => navigate(`/facilities/${chosen[key]}?tab=${tab}`)}>
                {t('common.open')}
              </Button>
            </div>
            {preview && (
              <button type="button" className="mt-2 self-start text-xs font-semibold text-azure-500 hover:underline"
                onClick={() => setPreviewing(previewing === key ? null : key)}>
                {previewing === key ? t('questionnaires.closePreview') : t('questionnaires.previewForm')}
              </button>
            )}
          </div>
        ))}
      </div>
      {previewing === 'gasSet' && <GasSetPreview />}
      {previewing === 'qcnc' && <QcncPreview />}
    </section>
  );
}

/** The empty GAS-Set as the Internal Facilitator fills it in; nothing is saved here. */
function GasSetPreview() {
  const { t } = useTranslation();
  const schema = useQuery({
    queryKey: ['instruments', 'gas-set', 'schema'],
    queryFn: async () => (await api.get<ProfileSchema>('/questionnaires/instruments/gas-set/schema')).data,
  });
  const [value, setValue] = useState<Record<string, unknown>>({});
  return (
    <div className="mt-4 rounded-xl border border-neutral-200 bg-white p-4 shadow-sm">
      <p className="mb-3 rounded-lg bg-gold-50 px-3 py-2 text-xs font-medium text-gold-900">
        {t('questionnaires.previewHint')}
      </p>
      {schema.isLoading && <Spinner label={t('common.loading')} />}
      {schema.data && <ProfileForm schema={schema.data} value={value} onChange={setValue} readOnly={false} />}
    </div>
  );
}

/** The QCNC standards catalogue by cluster, as assessed in the facility's QCNC tab. */
function QcncPreview() {
  const { t } = useTranslation();
  const catalogue = useQuery({
    queryKey: ['instruments', 'qcnc', 'catalogue'],
    queryFn: async () => (await api.get<QcncStandard[]>('/facilities/qcnc-standards/catalogue')).data,
  });
  const clusters = [...new Set((catalogue.data ?? []).map((s) => s.clusterCode))];
  return (
    <div className="mt-4 rounded-xl border border-neutral-200 bg-white p-4 shadow-sm">
      <p className="mb-3 rounded-lg bg-gold-50 px-3 py-2 text-xs font-medium text-gold-900">
        {t('questionnaires.qcncPreviewHint')}
      </p>
      {catalogue.isLoading && <Spinner label={t('common.loading')} />}
      {clusters.map((cluster) => (
        <div key={cluster} className="mb-4">
          <h3 className="mb-1 bg-blue-50 px-3 py-1.5 text-xs font-bold text-blue-700">{cluster}</h3>
          <ul className="divide-y divide-neutral-100 text-sm">
            {(catalogue.data ?? []).filter((s) => s.clusterCode === cluster).map((s) => (
              <li key={s.id} className="flex gap-3 px-3 py-1.5">
                <span className="w-10 shrink-0 font-mono text-xs font-bold text-blue-600">{s.letterCode}</span>
                <span className="flex-1 text-neutral-800">{s.description ?? '—'}</span>
                {s.mandatory && <StatusTag tone="gold">{t('questionnaires.mandatory')}</StatusTag>}
              </li>
            ))}
          </ul>
        </div>
      ))}
      {catalogue.data?.length === 0 && <p className="text-sm text-neutral-500">{t('common.empty')}</p>}
    </div>
  );
}
