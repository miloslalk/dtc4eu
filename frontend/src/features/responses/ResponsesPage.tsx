import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { FilterBar } from '../../components/FilterBar';
import { filterInputClass, SelectField, TextField } from '../../components/fields';
import { DataTable, type Column } from '../../components/DataTable';
import { StatusTag } from '../../components/StatusTag';
import { Modal } from '../../components/Modal';
import { useAuth } from '../../lib/auth';
import { useCycle } from '../../lib/cycle';
import { apiErrorMessage, downloadFile } from '../../lib/api';
import { useFacilities } from '../facilities/api';
import { useQuestionnaires } from '../questionnaires/api';
import {
  RESPONDENT_CATEGORIES, useCreateResponse, useResponses,
  type RespondentCategory, type ResponseFilters, type ResponseRow,
} from './api';

export function ResponsesPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { hasAnyRole } = useAuth();
  const { readOnly } = useCycle();
  const canExport = hasAnyRole('RESEARCH_TEAM', 'ADMIN', 'SUPER_ADMIN');

  const [filters, setFilters] = useState<ResponseFilters>({ page: 0 });
  const { data, isLoading } = useResponses(filters);
  const facilities = useFacilities({ page: 0 });
  const questionnaires = useQuestionnaires();
  const [createOpen, setCreateOpen] = useState(false);

  const columns: Column<ResponseRow>[] = [
    { key: 'id', header: '#', render: (r) => <span className="font-mono text-xs text-neutral-400">{r.id}</span> },
    { key: 'questionnaireCode', header: t('responses.questionnaire'), render: (r) => <span className="font-semibold">{r.questionnaireCode}</span> },
    { key: 'subjectCode', header: t('responses.subjectCode'), render: (r) => <span className="font-mono">{r.subjectCode}</span> },
    { key: 'facilityCode', header: t('responses.facility'), render: (r) => r.facilityCode ?? '—' },
    {
      key: 'respondentCategory',
      header: t('responses.respondentCategory'),
      render: (r) => <StatusTag tone="info">{t(`responses.categories.${r.respondentCategory}`)}</StatusTag>,
    },
    {
      key: 'status',
      header: t('responses.status'),
      render: (r) => (
        <StatusTag tone={r.status === 'SUBMITTED' ? 'gold' : 'neutral'}>
          {r.status === 'SUBMITTED' ? t('responses.submitted') : t('responses.draft')}
        </StatusTag>
      ),
    },
    { key: 'submittedAt', header: t('responses.submitted'), render: (r) => r.submittedAt?.slice(0, 10) ?? '—' },
  ];

  const handleExport = () =>
    downloadFile('/responses/export', {
      facilityId: filters.facilityId || undefined,
      questionnaireId: filters.questionnaireId || undefined,
      category: filters.category || undefined,
      status: filters.status || undefined,
    }, 'response-items.csv');

  return (
    <div>
      <PageHeader
        title={t('responses.title')}
        actions={
          <>
            {canExport && <Button variant="ghost" onClick={handleExport}>{t('responses.exportItems')}</Button>}
            {!readOnly && <Button onClick={() => setCreateOpen(true)}>{t('responses.new')}</Button>}
          </>
        }
      />

      <FilterBar onClear={() => setFilters({ page: 0 })}>
        <select className={filterInputClass} value={filters.questionnaireId ?? ''}
          onChange={(e) => setFilters({ ...filters, questionnaireId: e.target.value, page: 0 })}>
          <option value="">{t('responses.questionnaire')}: {t('common.all')}</option>
          {(questionnaires.data ?? []).map((q) => (
            <option key={q.id} value={q.id}>{q.code}</option>
          ))}
        </select>
        <select className={filterInputClass} value={filters.facilityId ?? ''}
          onChange={(e) => setFilters({ ...filters, facilityId: e.target.value, page: 0 })}>
          <option value="">{t('responses.facility')}: {t('common.all')}</option>
          {(facilities.data?.content ?? []).map((facility) => (
            <option key={facility.id} value={facility.id}>{facility.facilityCode}</option>
          ))}
        </select>
        <select className={filterInputClass} value={filters.category ?? ''}
          onChange={(e) => setFilters({ ...filters, category: e.target.value, page: 0 })}>
          <option value="">{t('responses.respondentCategory')}: {t('common.all')}</option>
          {RESPONDENT_CATEGORIES.map((category) => (
            <option key={category} value={category}>{t(`responses.categories.${category}`)}</option>
          ))}
        </select>
        <select className={filterInputClass} value={filters.status ?? ''}
          onChange={(e) => setFilters({ ...filters, status: e.target.value, page: 0 })}>
          <option value="">{t('responses.status')}: {t('common.all')}</option>
          <option value="DRAFT">{t('responses.draft')}</option>
          <option value="SUBMITTED">{t('responses.submitted')}</option>
        </select>
      </FilterBar>

      <DataTable
        columns={columns}
        rows={data?.content ?? []}
        rowKey={(r) => r.id}
        loading={isLoading}
        page={data?.page ?? 0}
        size={data?.size ?? 20}
        totalElements={data?.totalElements}
        onPageChange={(page) => setFilters({ ...filters, page })}
        onRowClick={(r) => navigate(`/responses/${r.id}`)}
      />

      <NewResponseModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </div>
  );
}

function NewResponseModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const facilities = useFacilities({ page: 0 });
  const questionnaires = useQuestionnaires();
  const create = useCreateResponse();

  const [form, setForm] = useState({ questionnaireId: '', facilityId: '', subjectCode: '', category: 'USERS' as RespondentCategory });
  const [error, setError] = useState<string | null>(null);

  const activeQuestionnaires = (questionnaires.data ?? []).filter((q) => q.active);

  const handleCreate = async () => {
    if (!form.questionnaireId || !form.subjectCode.trim()) {
      setError(t('common.required'));
      return;
    }
    try {
      const created = await create.mutateAsync({
        questionnaireId: Number(form.questionnaireId),
        facilityId: form.facilityId ? Number(form.facilityId) : null,
        subjectCode: form.subjectCode.trim(),
        respondentCategory: form.category,
      });
      onClose();
      navigate(`/responses/${created.id}`);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <Modal
      title={t('responses.new')}
      open={open}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={handleCreate} loading={create.isPending}>{t('common.create')}</Button>
        </>
      }
    >
      <div className="space-y-4">
        <SelectField label={t('responses.questionnaire')} required value={form.questionnaireId}
          onChange={(e) => setForm({ ...form, questionnaireId: e.target.value })}>
          <option value="">—</option>
          {activeQuestionnaires.map((q) => (
            <option key={q.id} value={q.id}>{q.code} — {q.name}</option>
          ))}
        </SelectField>
        <SelectField label={t('responses.facility')} value={form.facilityId}
          onChange={(e) => setForm({ ...form, facilityId: e.target.value })}>
          <option value="">—</option>
          {(facilities.data?.content ?? []).map((facility) => (
            <option key={facility.id} value={facility.id}>{facility.facilityCode} — {facility.name}</option>
          ))}
        </SelectField>
        <TextField label={t('responses.subjectCode')} required placeholder="SUBJ-001" value={form.subjectCode}
          onChange={(e) => setForm({ ...form, subjectCode: e.target.value })} />
        <SelectField label={t('responses.respondentCategory')} required value={form.category}
          onChange={(e) => setForm({ ...form, category: e.target.value as RespondentCategory })}>
          {RESPONDENT_CATEGORIES.map((category) => (
            <option key={category} value={category}>{t(`responses.categories.${category}`)}</option>
          ))}
        </SelectField>
        <p className="text-xs text-neutral-500">{t('responses.pseudonymHint')}</p>
        {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
      </div>
    </Modal>
  );
}
