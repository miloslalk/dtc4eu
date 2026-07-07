import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { TextField } from '../../components/fields';
import { DataTable, type Column } from '../../components/DataTable';
import { StatusTag } from '../../components/StatusTag';
import { apiErrorMessage } from '../../lib/api';
import { useCreateQuestionnaire, useQuestionnaires, type QuestionnaireRow } from './api';

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
          <div className="grid grid-cols-2 gap-3">
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
